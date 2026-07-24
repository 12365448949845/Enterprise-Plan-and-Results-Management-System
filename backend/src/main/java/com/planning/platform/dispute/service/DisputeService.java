package com.planning.platform.dispute.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.dispute.domain.BizDisputeCase;
import com.planning.platform.dispute.domain.BizDisputeOpinion;
import com.planning.platform.dispute.domain.BizDisputeReviewer;
import com.planning.platform.dispute.dto.DisputeDecisionReqDTO;
import com.planning.platform.dispute.dto.DisputeOpinionReqDTO;
import com.planning.platform.dispute.dto.DisputeRecusalReqDTO;
import com.planning.platform.dispute.dto.DisputeReviewerReqDTO;
import com.planning.platform.dispute.mapper.BizDisputeCaseMapper;
import com.planning.platform.dispute.mapper.BizDisputeOpinionMapper;
import com.planning.platform.dispute.mapper.BizDisputeReviewerMapper;
import com.planning.platform.dispute.vo.DisputeVO;
import com.planning.platform.employee.service.EmployeeAppealPackageService;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.performance.service.PerformanceRoleGuard;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysUserMapper;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DisputeService {
    private final PerformanceRoleGuard roleGuard;
    private final PerformanceDataScopeService dataScopeService;
    private final BizDisputeCaseMapper caseMapper;
    private final BizDisputeReviewerMapper reviewerMapper;
    private final BizDisputeOpinionMapper opinionMapper;
    private final BizEmployeeAppealMapper appealMapper;
    private final BizResultMapper resultMapper;
    private final EmployeeAppealPackageService employeeAppealPackageService;
    private final SysUserMapper userMapper;
    private final AuditLogService auditLogService;
    private final UserMessageService messageService;

    public DisputeVO.DashboardVO dashboard(AuthUser user) {
        roleGuard.requireDisputeModule(user);
        syncCases(user);
        List<BizDisputeCase> cases = visibleCases(user, null, null, null);
        return new DisputeVO.DashboardVO(
                List.of(
                        metric("SUBMITTED", "待处理", cases, "warning"),
                        metric("REVIEWING", "评审中", cases, "primary"),
                        metric("NEEDS_SUPPLEMENT", "待补充", cases, "danger"),
                        metric("DECIDED", "已裁决", cases, "success")
                ),
                cases.stream().limit(8).map(this::toCaseItem).toList()
        );
    }

    public List<DisputeVO.CaseItemVO> cases(AuthUser user, String status, String period, String keyword) {
        roleGuard.requireDisputeModule(user);
        syncCases(user);
        return visibleCases(user, status, period, keyword).stream().map(this::toCaseItem).toList();
    }

    public DisputeVO.DetailVO detail(AuthUser user, Long id) {
        roleGuard.requireDisputeModule(user);
        BizDisputeCase item = requireVisibleCase(user, id);
        BizEmployeeAppeal appeal = appealMapper.selectById(item.getAppealId());
        BizResult result = appeal == null || appeal.getRelatedResultId() == null
                ? null : resultMapper.selectById(appeal.getRelatedResultId());
        List<BizDisputeReviewer> reviewers = reviewers(item.getId());
        List<BizDisputeOpinion> opinions = opinions(item.getId());
        Map<Long, SysUser> users = dataScopeService.userMap();
        boolean canDecide = isLead(user) && item.getStatus() != null
                && !Set.of("DECIDED", "ARCHIVED").contains(item.getStatus())
                && validPanel(reviewers) && opinions.size() >= reviewers.size()
                && reviewers.stream().noneMatch(r -> "PENDING".equals(r.getRecusalStatus()));
        return new DisputeVO.DetailVO(
                toCaseItem(item), appeal == null ? "" : defaultText(appeal.getReason()),
                appeal == null ? "" : appeal.getStatus(), result == null ? null : result.getId(),
                result == null ? "" : result.getTitle(), result == null ? "" : result.getStatus(),
                List.of("原计划与审批记录", "成果版本与证据", "申诉与处理记录", "绩效台账影响", "操作审计日志"),
                reviewers.stream().map(r -> toReviewer(r, users, user.userId())).toList(),
                opinions.stream().map(o -> toOpinion(o, users)).toList(),
                canDecide, item.getDecision(), item.getDecisionComment()
        );
    }

    public List<DisputeVO.ReviewerVO> reviewerList(AuthUser user, Long caseId) {
        roleGuard.requireDisputeModule(user);
        requireVisibleCase(user, caseId);
        Map<Long, SysUser> users = dataScopeService.userMap();
        return reviewers(caseId).stream().map(item -> toReviewer(item, users, user.userId())).toList();
    }

    public List<DisputeVO.ReviewerCandidateVO> reviewerCandidates(AuthUser user, Long caseId, String keyword) {
        roleGuard.requireDisputeManager(user);
        BizDisputeCase item = requireVisibleCase(user, caseId);
        Set<Long> existing = reviewers(caseId).stream().map(BizDisputeReviewer::getUserId).collect(Collectors.toSet());
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        return dataScopeService.activeUsers().stream()
                .filter(candidate -> !candidate.getId().equals(item.getOwnerUserId()))
                .filter(candidate -> !existing.contains(candidate.getId()))
                .filter(candidate -> normalizedKeyword.isBlank()
                        || contains(candidate.getRealName(), normalizedKeyword)
                        || contains(candidate.getEmployeeNo(), normalizedKeyword))
                .sorted((left, right) -> {
                    boolean leftSameDept = item.getDeptId() != null && item.getDeptId().equals(left.getDeptId());
                    boolean rightSameDept = item.getDeptId() != null && item.getDeptId().equals(right.getDeptId());
                    if (leftSameDept != rightSameDept) return leftSameDept ? -1 : 1;
                    return left.getRealName().compareToIgnoreCase(right.getRealName());
                })
                .limit(50)
                .map(candidate -> new DisputeVO.ReviewerCandidateVO(
                        candidate.getId(), candidate.getEmployeeNo(), candidate.getRealName(), candidate.getDeptId()))
                .toList();
    }

    public ResponseEntity<Resource> downloadPackage(AuthUser user, Long caseId) {
        roleGuard.requireDisputeModule(user);
        BizDisputeCase item = requireVisibleCase(user, caseId);
        if (!"READY".equals(item.getPackageStatus())) {
            throw new BizException(409, "裁决资料包尚未生成完成");
        }
        auditLogService.success(user, "DISPUTE_PACKAGE_DOWNLOAD", "DISPUTE_CASE", caseId,
                "caseNo=" + item.getCaseNo());
        return employeeAppealPackageService.downloadForHandler(user, item.getAppealId());
    }

    @Transactional
    public DisputeVO.ReviewerVO addReviewer(AuthUser user, Long caseId, DisputeReviewerReqDTO request) {
        roleGuard.requireDisputeManager(user);
        BizDisputeCase item = requireVisibleCase(user, caseId);
        List<BizDisputeReviewer> existing = reviewers(caseId);
        if (existing.size() >= 5) throw new BizException(422, "评审小组最多 5 人");
        if (existing.stream().anyMatch(r -> r.getUserId().equals(request.getUserId()))) {
            throw new BizException(409, "该成员已在评审小组中");
        }
        SysUser candidate = userMapper.selectById(request.getUserId());
        if (candidate == null || !Integer.valueOf(1).equals(candidate.getStatus())
                || Integer.valueOf(1).equals(candidate.getDeleted())) {
            throw new BizException(404, "评审成员不存在或已停用");
        }
        if (candidate.getId().equals(item.getOwnerUserId())) {
            throw new BizException(422, "案件当事人不能加入评审小组");
        }
        BizDisputeReviewer reviewer = new BizDisputeReviewer();
        reviewer.setCaseId(caseId);
        reviewer.setUserId(candidate.getId());
        reviewer.setSourceType("MANUAL");
        reviewer.setRecusalStatus("ACTIVE");
        reviewer.setDeleted(0);
        reviewerMapper.insert(reviewer);
        messageService.createNotice(candidate.getId(), "DISPUTE_REVIEWER_ASSIGNED", "你被加入裁决评审小组",
                user.realName() + "已将你加入案件“" + item.getDisputeSubject() + "”的评审小组。",
                "DISPUTE_CASE", String.valueOf(caseId), "/dispute/cases/" + caseId,
                item.getDeptId(), user.userId());
        if ("SUBMITTED".equals(item.getStatus())) {
            item.setStatus("REVIEWING");
            caseMapper.updateById(item);
        }
        auditLogService.success(user, "DISPUTE_REVIEWER_ADD", "DISPUTE_CASE", caseId,
                "{\"userId\":" + candidate.getId() + "}");
        return toReviewer(reviewer, Map.of(candidate.getId(), candidate), user.userId());
    }

    @Transactional
    public void removeReviewer(AuthUser user, Long caseId, Long reviewerId) {
        roleGuard.requireDisputeManager(user);
        requireVisibleCase(user, caseId);
        BizDisputeReviewer reviewer = reviewerMapper.selectById(reviewerId);
        if (reviewer == null || !caseId.equals(reviewer.getCaseId()) || Integer.valueOf(1).equals(reviewer.getDeleted())) {
            throw new BizException(404, "评审成员不存在");
        }
        Long removedUserId = reviewer.getUserId();
        reviewer.setDeleted(1);
        reviewerMapper.updateById(reviewer);
        messageService.createNotice(removedUserId, "DISPUTE_REVIEWER_REMOVED", "你已被移出裁决评审小组",
                user.realName() + "已将你移出该案件的评审小组。",
                "DISPUTE_CASE", String.valueOf(caseId), "/dispute/cases",
                null, user.userId());
        auditLogService.success(user, "DISPUTE_REVIEWER_REMOVE", "DISPUTE_CASE", caseId,
                "{\"reviewerId\":" + reviewerId + "}");
    }

    @Transactional
    public void recuse(AuthUser user, Long caseId, DisputeRecusalReqDTO request) {
        roleGuard.requireDisputeModule(user);
        BizDisputeCase item = requireVisibleCase(user, caseId);
        BizDisputeReviewer reviewer = reviewerMapper.selectOne(new LambdaQueryWrapper<BizDisputeReviewer>()
                .eq(BizDisputeReviewer::getCaseId, caseId)
                .eq(BizDisputeReviewer::getUserId, user.userId())
                .eq(BizDisputeReviewer::getDeleted, 0));
        if (reviewer == null) throw new BizException(403, "当前用户不是该案件评审成员");
        reviewer.setRecusalStatus("PENDING");
        reviewer.setRecusalReason(requireText(request.getReason(), "回避原因不能为空"));
        reviewerMapper.updateById(reviewer);
        Long managerId = dataScopeService.departmentOwnerId(item.getDeptId());
        messageService.createTodo(managerId, "DISPUTE_RECUSAL", "评审员申请回避",
                user.realName() + "申请回避案件“" + item.getDisputeSubject() + "”。",
                "DISPUTE_CASE", String.valueOf(caseId), item.getDeadlineAt(),
                "查看回避原因并调整评审小组", "未处理将阻止案件裁决",
                "/dispute/cases/" + caseId, item.getDeptId(), user.userId());
        auditLogService.success(user, "DISPUTE_REVIEWER_RECUSE", "DISPUTE_CASE", caseId,
                "{\"reason\":\"" + escape(request.getReason()) + "\"}");
    }

    @Transactional
    public DisputeVO.OpinionVO saveOpinion(AuthUser user, Long caseId, DisputeOpinionReqDTO request) {
        roleGuard.requireDisputeModule(user);
        BizDisputeCase item = requireVisibleCase(user, caseId);
        BizDisputeReviewer reviewer = reviewerMapper.selectOne(new LambdaQueryWrapper<BizDisputeReviewer>()
                .eq(BizDisputeReviewer::getCaseId, caseId)
                .eq(BizDisputeReviewer::getUserId, user.userId())
                .eq(BizDisputeReviewer::getDeleted, 0));
        if (reviewer == null || "PENDING".equals(reviewer.getRecusalStatus())) {
            throw new BizException(403, "当前用户不能对该案件提交意见");
        }
        if (Set.of("DECIDED", "ARCHIVED").contains(item.getStatus())) throw new BizException(409, "案件已结束");
        BizDisputeOpinion opinion = opinionMapper.selectOne(new LambdaQueryWrapper<BizDisputeOpinion>()
                .eq(BizDisputeOpinion::getCaseId, caseId)
                .eq(BizDisputeOpinion::getReviewerId, reviewer.getId())
                .eq(BizDisputeOpinion::getDeleted, 0));
        if (opinion == null) {
            opinion = new BizDisputeOpinion();
            opinion.setCaseId(caseId);
            opinion.setReviewerId(reviewer.getId());
            opinion.setVersionNo(1);
            opinion.setCreatedBy(user.userId());
            opinion.setDeleted(0);
        } else {
            opinion.setVersionNo(opinion.getVersionNo() + 1);
        }
        opinion.setOpinion(normalizeOpinion(request.getOpinion()));
        opinion.setComment(requireText(request.getComment(), "评审意见不能为空"));
        opinion.setSubmittedAt(LocalDateTime.now());
        opinion.setUpdatedBy(user.userId());
        if (opinion.getId() == null) {
            opinionMapper.insert(opinion);
        } else {
            opinionMapper.updateById(opinion);
        }
        auditLogService.success(user, "DISPUTE_OPINION_SUBMIT", "DISPUTE_CASE", caseId,
                "{\"opinion\":\"" + escape(opinion.getOpinion()) + "\"}");
        List<BizDisputeReviewer> panel = reviewers(caseId);
        long submittedCount = opinionMapper.selectCount(new LambdaQueryWrapper<BizDisputeOpinion>()
                .eq(BizDisputeOpinion::getCaseId, caseId)
                .eq(BizDisputeOpinion::getDeleted, 0));
        if (validPanel(panel) && submittedCount >= panel.size()) {
            Long managerId = dataScopeService.departmentOwnerId(item.getDeptId());
            messageService.createNoticeOnce(managerId, "DISPUTE_PANEL_READY", "评审意见已全部提交",
                    "案件“" + item.getDisputeSubject() + "”的评审意见已全部提交，可以进行最终裁决。",
                    "DISPUTE_CASE", String.valueOf(caseId), "/dispute/cases/" + caseId,
                    item.getDeptId(), user.userId());
        }
        return toOpinion(opinion, Map.of(user.userId(), userMapper.selectById(user.userId())));
    }

    @Transactional
    public void decide(AuthUser user, Long caseId, DisputeDecisionReqDTO request) {
        roleGuard.requireDisputeManager(user);
        BizDisputeCase item = requireVisibleCase(user, caseId);
        if (Set.of("DECIDED", "ARCHIVED").contains(item.getStatus())) throw new BizException(409, "案件已结束");
        List<BizDisputeReviewer> reviewers = reviewers(caseId);
        if (!validPanel(reviewers)) throw new BizException(422, "评审小组人数必须为 2-5 人");
        if (reviewers.stream().anyMatch(r -> "PENDING".equals(r.getRecusalStatus()))) {
            throw new BizException(422, "评审小组存在未处理的回避状态");
        }
        long opinionCount = opinionMapper.selectCount(new LambdaQueryWrapper<BizDisputeOpinion>()
                .eq(BizDisputeOpinion::getCaseId, caseId).eq(BizDisputeOpinion::getDeleted, 0));
        if (opinionCount < reviewers.size()) throw new BizException(422, "所有有效评审成员提交意见后才能裁决");
        String decision = normalizeDecision(request.getDecision());
        item.setStatus("DECIDED");
        item.setDecision(decision);
        item.setDecisionComment(requireText(request.getComment(), "裁决理由不能为空"));
        item.setDecidedBy(user.userId());
        item.setDecidedAt(LocalDateTime.now());
        item.setUpdatedBy(user.userId());
        caseMapper.updateById(item);
        String decisionText = "SUPPORT".equals(decision) ? "支持申诉"
                : "REJECT".equals(decision) ? "驳回申诉" : "需要补充材料";
        messageService.createNotice(item.getOwnerUserId(), "DISPUTE_DECISION_RESULT", "申诉裁决结果已产生",
                "案件“" + item.getDisputeSubject() + "”的裁决结果为“" + decisionText + "”，请查看裁决理由。",
                "DISPUTE_CASE_RESULT", String.valueOf(caseId), "/employee/appeals",
                item.getDeptId(), user.userId());
        for (BizDisputeReviewer reviewer : reviewers) {
            messageService.createNotice(reviewer.getUserId(), "DISPUTE_DECISION_RESULT", "参与案件已完成裁决",
                    "案件“" + item.getDisputeSubject() + "”已完成裁决，结果为“" + decisionText + "”。",
                    "DISPUTE_CASE_RESULT", String.valueOf(caseId), "/dispute/cases/" + caseId,
                    item.getDeptId(), user.userId());
        }
        auditLogService.success(user, "DISPUTE_DECISION_SUBMIT", "DISPUTE_CASE", caseId,
                "{\"decision\":\"" + escape(decision) + "\"}");
    }

    private void syncCases(AuthUser user) {
        List<BizEmployeeAppeal> appeals = appealMapper.selectList(new LambdaQueryWrapper<BizEmployeeAppeal>()
                .eq(BizEmployeeAppeal::getDeleted, 0)
                .in(BizEmployeeAppeal::getStatus, List.of("SUBMITTED", "PROCESSING", "RESOLVED")));
        for (BizEmployeeAppeal appeal : appeals) {
            boolean exists = caseMapper.selectCount(new LambdaQueryWrapper<BizDisputeCase>()
                    .eq(BizDisputeCase::getAppealId, appeal.getId())
                    .eq(BizDisputeCase::getDeleted, 0)) > 0;
            if (exists) continue;
            BizDisputeCase item = new BizDisputeCase();
            item.setCaseNo("DC-" + LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            item.setAppealId(appeal.getId());
            item.setOwnerUserId(appeal.getOwnerUserId());
            item.setDeptId(appeal.getDeptId());
            item.setPeriodStart(appeal.getCreatedAt() == null ? LocalDate.now().withDayOfMonth(1) : appeal.getCreatedAt().toLocalDate().withDayOfMonth(1));
            item.setPeriodEnd(item.getPeriodStart().plusMonths(1).minusDays(1));
            item.setDisputeSubject(appeal.getTitle());
            item.setStatus("SUBMITTED");
            item.setPackageStatus("READY");
            item.setDeadlineAt(appeal.getCreatedAt() == null ? null : appeal.getCreatedAt().plusDays(7));
            item.setCreatedBy(user.userId());
            item.setUpdatedBy(user.userId());
            item.setDeleted(0);
            caseMapper.insert(item);
            Long managerId = dataScopeService.departmentOwnerId(appeal.getDeptId());
            messageService.createNoticeOnce(managerId, "DISPUTE_CASE_CREATED", "新增争议裁决案件",
                    "员工申诉“" + appeal.getTitle() + "”已进入裁决流程，请配置评审小组。",
                    "DISPUTE_CASE", String.valueOf(item.getId()), "/dispute/cases/" + item.getId(),
                    item.getDeptId(), user.userId());
        }
    }

    private List<BizDisputeCase> visibleCases(AuthUser user, String status, String period, String keyword) {
        LambdaQueryWrapper<BizDisputeCase> query = new LambdaQueryWrapper<BizDisputeCase>()
                .eq(BizDisputeCase::getDeleted, 0)
                .eq(StringUtils.hasText(status), BizDisputeCase::getStatus, status)
                .like(StringUtils.hasText(keyword), BizDisputeCase::getDisputeSubject, keyword)
                .orderByDesc(BizDisputeCase::getCreatedAt);
        List<BizDisputeCase> cases = caseMapper.selectList(query);
        Set<Long> assigned = reviewerMapper.selectList(new LambdaQueryWrapper<BizDisputeReviewer>()
                        .eq(BizDisputeReviewer::getUserId, user.userId())
                        .eq(BizDisputeReviewer::getDeleted, 0))
                .stream().map(BizDisputeReviewer::getCaseId).collect(Collectors.toSet());
        Set<Long> allowed = user.roles().contains("SUPER_ADMIN")
                ? cases.stream().map(BizDisputeCase::getId).collect(Collectors.toSet())
                : cases.stream()
                .filter(item -> (user.deptId() != null && user.deptId().equals(item.getDeptId())) || assigned.contains(item.getId()))
                .map(BizDisputeCase::getId).collect(Collectors.toSet());
        return cases.stream().filter(item -> allowed.contains(item.getId()))
                .filter(item -> !StringUtils.hasText(period) || period.equals(item.getPeriodStart().toString().substring(0, 7)))
                .toList();
    }

    private BizDisputeCase requireVisibleCase(AuthUser user, Long id) {
        BizDisputeCase item = caseMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) throw new BizException(404, "裁决案件不存在");
        boolean assigned = reviewerMapper.selectCount(new LambdaQueryWrapper<BizDisputeReviewer>()
                .eq(BizDisputeReviewer::getCaseId, id)
                .eq(BizDisputeReviewer::getUserId, user.userId())
                .eq(BizDisputeReviewer::getDeleted, 0)) > 0;
        if (!user.roles().contains("SUPER_ADMIN")
                && !assigned
                && (user.deptId() == null || !user.deptId().equals(item.getDeptId()))) {
            throw new BizException(403, "当前账号无权访问该案件");
        }
        return item;
    }

    private DisputeVO.CaseItemVO toCaseItem(BizDisputeCase item) {
        BizEmployeeAppeal appeal = appealMapper.selectById(item.getAppealId());
        SysUser owner = userMapper.selectById(item.getOwnerUserId());
        List<BizDisputeReviewer> reviewers = reviewers(item.getId());
        return new DisputeVO.CaseItemVO(item.getId(), item.getCaseNo(), item.getAppealId(),
                owner == null ? "" : owner.getRealName(), String.valueOf(item.getDeptId()),
                item.getPeriodStart(), item.getPeriodEnd(), item.getDisputeSubject(),
                appeal == null ? "" : appeal.getTitle(), item.getStatus(), item.getPackageStatus(),
                item.getDeadlineAt(), reviewers.size(), opinions(item.getId()).size());
    }

    private DisputeVO.ReviewerVO toReviewer(BizDisputeReviewer item, Map<Long, SysUser> users, Long currentUserId) {
        SysUser user = users.get(item.getUserId());
        return new DisputeVO.ReviewerVO(item.getId(), item.getUserId(),
                user == null ? "" : user.getRealName(), item.getSourceType(), item.getRecusalStatus(),
                item.getRecusalReason(), item.getUserId().equals(currentUserId));
    }

    private DisputeVO.OpinionVO toOpinion(BizDisputeOpinion item, Map<Long, SysUser> users) {
        BizDisputeReviewer reviewer = reviewerMapper.selectById(item.getReviewerId());
        SysUser user = reviewer == null ? null : users.get(reviewer.getUserId());
        return new DisputeVO.OpinionVO(item.getId(), item.getReviewerId(), user == null ? "" : user.getRealName(),
                item.getOpinion(), item.getComment(), item.getVersionNo(), item.getSubmittedAt());
    }

    private List<BizDisputeReviewer> reviewers(Long caseId) {
        return reviewerMapper.selectList(new LambdaQueryWrapper<BizDisputeReviewer>()
                .eq(BizDisputeReviewer::getCaseId, caseId).eq(BizDisputeReviewer::getDeleted, 0)
                .orderByAsc(BizDisputeReviewer::getJoinedAt));
    }

    private List<BizDisputeOpinion> opinions(Long caseId) {
        return opinionMapper.selectList(new LambdaQueryWrapper<BizDisputeOpinion>()
                .eq(BizDisputeOpinion::getCaseId, caseId).eq(BizDisputeOpinion::getDeleted, 0)
                .orderByDesc(BizDisputeOpinion::getSubmittedAt));
    }

    private DisputeVO.MetricVO metric(String status, String label, List<BizDisputeCase> cases, String tone) {
        return new DisputeVO.MetricVO(status, label, (int) cases.stream().filter(item -> status.equals(item.getStatus())).count(), tone);
    }

    private boolean validPanel(List<BizDisputeReviewer> reviewers) {
        return reviewers.size() >= 2 && reviewers.size() <= 5;
    }

    private boolean isLead(AuthUser user) {
        return user.roles().contains("SUPER_ADMIN")
                || user.roles().contains("DEPT_OWNER")
                || user.roles().contains("DEPT_LEADER");
    }

    private String normalizeOpinion(String value) {
        if (!Set.of("SUPPORT", "REJECT", "SUPPLEMENT").contains(value)) throw new BizException(422, "评审意见类型不合法");
        return value;
    }

    private String normalizeDecision(String value) {
        if (!Set.of("SUPPORT", "REJECT", "SUPPLEMENT").contains(value)) throw new BizException(422, "裁决结果不合法");
        return value;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) throw new BizException(422, message);
        return value.trim();
    }

    private String defaultText(String value) { return value == null ? "" : value; }
    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }
    private String escape(String value) { return value == null ? "" : value.replace("\"", "\\\""); }
}
