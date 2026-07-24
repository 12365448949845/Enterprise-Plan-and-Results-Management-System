package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.domain.BizPlanAdjustment;
import com.planning.platform.performance.domain.BizScoreRule;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.dto.BatchActionReqDTO;
import com.planning.platform.performance.dto.ExportTaskCreateReqDTO;
import com.planning.platform.performance.dto.PerformanceActionReqDTO;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.performance.mapper.BizScoreRuleMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.vo.PerformanceVO.ActionResultVO;
import com.planning.platform.performance.vo.PerformanceVO.DailyReviewItemVO;
import com.planning.platform.performance.vo.PerformanceVO.EvidenceFileVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportDownloadVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportTaskVO;
import com.planning.platform.performance.vo.PerformanceVO.ExtraMonthPlanApprovalVO;
import com.planning.platform.performance.vo.PerformanceVO.LeaderDateStatusVO;
import com.planning.platform.performance.vo.PerformanceVO.LeaderWorkbenchVO;
import com.planning.platform.performance.vo.PerformanceVO.LedgerItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MetricVO;
import com.planning.platform.performance.vo.PerformanceVO.OrgNodeVO;
import com.planning.platform.performance.vo.PerformanceVO.PlanAdjustmentItemVO;
import com.planning.platform.performance.vo.PerformanceVO.ResultSuggestionItemVO;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.domain.SysDept;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaderPerformanceService {

    private static final Set<String> EXPORT_FORMATS = Set.of("PDF", "WORD", "ZIP");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> EXPORT_DIMENSIONS = Set.of(
            "SUBORDINATE_LEDGER", "PERSON_LEDGER", "DAILY_REVIEW_LIST",
            "RESULT_SUGGESTION_LIST", "PLAN_ADJUSTMENT_LIST"
    );

    private final PerformanceRoleGuard roleGuard;
    private final PerformanceDataScopeService dataScopeService;
    private final PerformanceJsonCodec jsonCodec;
    private final BizDayPlanMapper dayPlanMapper;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper resultEvidenceMapper;
    private final BizEmployeeAppealMapper appealMapper;
    private final BizPlanAdjustmentMapper planAdjustmentMapper;
    private final BizScoreRuleMapper scoreRuleMapper;
    private final BizTodoMapper todoMapper;
    private final BizExportTaskMapper exportTaskMapper;
    private final ExportFileService exportFileService;
    private final AuditLogService auditLogService;
    private final UserMessageService messageService;

    public List<OrgNodeVO> orgTree(AuthUser user) {
        roleGuard.requireLeaderModule(user);
        return dataScopeService.orgTree(user, false);
    }

    public LeaderWorkbenchVO workbench(AuthUser user, Long scopeOrgId, LocalDate date, String periodMonth) {
        roleGuard.requireLeaderModule(user);
        PeriodRange selectedPeriod = workbenchPeriod(date, periodMonth);
        List<DailyReviewItemVO> reviews = dailyReviews(user, scopeOrgId,
                selectedPeriod.start(), selectedPeriod.end(), null, null);
        List<ResultSuggestionItemVO> suggestions = resultSuggestions(user, scopeOrgId,
                selectedPeriod.start(), selectedPeriod.end(), null, null);
        return new LeaderWorkbenchVO(
                dataScopeService.orgTree(user, false),
                leaderMetrics(reviews, suggestions),
                leaderDateStatuses(reviews, suggestions),
                reviews.stream().limit(5).toList()
        );
    }

    public List<DailyReviewItemVO> dailyReviews(AuthUser user, Long scopeOrgId, LocalDate startDate, LocalDate endDate,
                                                String reviewStatus, Boolean missingOnly) {
        roleGuard.requireLeaderModule(user);
        Set<Long> ownerIds = dataScopeService.leaderOwnerIds(user, scopeOrgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        List<BizDayPlan> plans = dayPlanMapper.selectList(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .in(BizDayPlan::getOwnerUserId, ownerIds)
                .ne(BizDayPlan::getStatus, "DRAFT")
                .ge(startDate != null, BizDayPlan::getPlanDate, startDate)
                .le(endDate != null, BizDayPlan::getPlanDate, endDate)
                .orderByDesc(BizDayPlan::getPlanDate)
                .orderByDesc(BizDayPlan::getId));
        Map<Long, SysUser> users = dataScopeService.userMap();
        Map<Long, BizMonthPlanItem> monthItems = monthItemMap(plans.stream()
                .map(BizDayPlan::getMonthPlanItemId)
                .filter(id -> id != null)
                .toList());
        return plans.stream()
                .map(plan -> toDailyReview(plan, users.get(plan.getOwnerUserId()), monthItems.get(plan.getMonthPlanItemId())))
                .filter(item -> !StringUtils.hasText(reviewStatus) || reviewStatus.equals(item.reviewStatus()))
                .filter(item -> missingOnly == null
                        || Boolean.TRUE.equals(missingOnly) && !item.missingFields().isEmpty()
                        || Boolean.FALSE.equals(missingOnly) && item.missingFields().isEmpty())
                .toList();
    }

    public DailyReviewItemVO dailyReviewDetail(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizDayPlan plan = requireDayPlan(id);
        dataScopeService.requireLeaderOwner(user, plan.getOwnerUserId());
        SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
        BizMonthPlanItem item = plan.getMonthPlanItemId() == null ? null : monthPlanItemMapper.selectById(plan.getMonthPlanItemId());
        return toDailyReview(plan, owner, item);
    }

    @Transactional
    public ActionResultVO commentDailyPlan(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizDayPlan plan = requireLeaderDayPlanForUpdate(user, id);
        requirePendingLeaderReview(plan);
        String comment = requireActionComment(request.getComment(), "点评内容不能为空", 500);
        LocalDateTime now = LocalDateTime.now();
        plan.setReviewStatus("COMMENTED");
        plan.setRiskLevel(normalizeRiskLevel(request.getRiskLevel(), plan.getRiskLevel()));
        plan.setApprovalComment(comment);
        plan.setReviewedBy(user.userId());
        plan.setReviewedAt(now);
        plan.setApproverId(user.userId());
        plan.setApproveAt(now);
        plan.setStatus("APPROVED");
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        completeObjectTodos("DAY_PLAN", id);
        messageService.createNotice(plan.getOwnerUserId(), "DAY_PLAN_COMMENT_RESULT", "日计划已完成点评",
                user.realName() + "已点评" + plan.getPlanDate() + "的日计划，请查看点评意见。",
                "DAY_PLAN_RESULT", id, "/employee/day-plans?date=" + plan.getPlanDate(),
                plan.getDeptId(), user.userId());
        return actionResult(id, "COMMENTED", "日计划点评已提交，并已同步到员工端。", "DAY_PLAN_COMMENT");
    }

    @Transactional
    public ActionResultVO markDailyRisk(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizDayPlan plan = requireLeaderDayPlanForUpdate(user, id);
        requirePendingLeaderReview(plan);
        String comment = requireActionComment(request.getComment(), "风险说明不能为空", 500);
        plan.setReviewStatus("RISK_MARKED");
        plan.setRiskLevel(normalizeRiskLevel(request.getRiskLevel(), "MEDIUM"));
        plan.setApprovalComment(comment);
        plan.setReviewedBy(user.userId());
        plan.setReviewedAt(LocalDateTime.now());
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        completeObjectTodos("DAY_PLAN", id);
        createRiskTodo(user, plan);
        messageService.createNotice(plan.getOwnerUserId(), "DAY_PLAN_RISK_NOTICE", "日计划已进入风险补审",
                user.realName() + "标记了" + plan.getPlanDate() + "的日计划风险，现已转交部门负责人补审。",
                "DAY_PLAN_RESULT", id, "/employee/day-plans?date=" + plan.getPlanDate(),
                plan.getDeptId(), user.userId());
        return actionResult(id, "RISK_MARKED", "日计划风险已标记并进入部门待办。", "DAY_PLAN_RISK_MARK");
    }

    @Transactional
    public List<ActionResultVO> batchCommentDailyPlans(AuthUser user, BatchActionReqDTO request) {
        List<ActionResultVO> results = new ArrayList<>();
        for (String id : request.getIds()) {
            PerformanceActionReqDTO action = new PerformanceActionReqDTO();
            action.setComment(request.getComment());
            action.setRiskLevel(request.getRiskLevel());
            results.add(commentDailyPlan(user, id, action));
        }
        return results;
    }

    @Transactional
    public List<ActionResultVO> batchMarkDailyRisks(AuthUser user, BatchActionReqDTO request) {
        List<ActionResultVO> results = new ArrayList<>();
        for (String id : request.getIds()) {
            PerformanceActionReqDTO action = new PerformanceActionReqDTO();
            action.setComment(request.getComment());
            action.setRiskLevel(request.getRiskLevel());
            results.add(markDailyRisk(user, id, action));
        }
        return results;
    }

    public List<ResultSuggestionItemVO> resultSuggestions(AuthUser user, Long scopeOrgId, LocalDate startDate,
                                                          LocalDate endDate, String suggestionStatus,
                                                          String evidenceStatus) {
        roleGuard.requireLeaderModule(user);
        Set<Long> ownerIds = dataScopeService.leaderOwnerIds(user, scopeOrgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .in(BizResult::getOwnerUserId, ownerIds)
                .ne(BizResult::getStatus, "DRAFT")
                .ge(startDate != null, BizResult::getSubmitAt, startDate == null ? null : startDate.atStartOfDay())
                .lt(endDate != null, BizResult::getSubmitAt, endDate == null ? null : endDate.plusDays(1).atStartOfDay())
                .orderByDesc(BizResult::getSubmitAt)
                .orderByDesc(BizResult::getId));
        Map<Long, SysUser> users = dataScopeService.userMap();
        Map<Long, BizMonthPlan> plans = monthPlanMap(results.stream().map(BizResult::getPlanId).filter(id -> id != null).toList());
        return results.stream()
                .map(result -> toResultSuggestion(result, users.get(result.getOwnerUserId()), plans.get(result.getPlanId())))
                .filter(item -> !StringUtils.hasText(suggestionStatus) || suggestionStatus.equals(item.suggestionStatus()))
                .filter(item -> !StringUtils.hasText(evidenceStatus) || evidenceStatus.equals(item.evidenceStatus()))
                .toList();
    }

    public ResultSuggestionItemVO resultSuggestionDetail(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizResult result = requireResult(id);
        dataScopeService.requireLeaderOwner(user, result.getOwnerUserId());
        SysUser owner = dataScopeService.requireUser(result.getOwnerUserId());
        BizMonthPlan plan = result.getPlanId() == null ? null : monthPlanMapper.selectById(result.getPlanId());
        return toResultSuggestion(result, owner, plan);
    }

    @Transactional
    public ActionResultVO submitResultSuggestion(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizResult result = requireLeaderResultForUpdate(user, id);
        if (!"PENDING".equals(result.getStatus())) {
            throw new BizException(409, "成果已完成最终处理，不能再提交确认建议");
        }
        if (!"PENDING_SUGGEST".equals(defaultText(result.getSuggestionStatus(), "PENDING_SUGGEST"))) {
            throw new BizException(409, "成果确认建议已提交，不能重复处理");
        }
        String decision = StringUtils.hasText(request.getDecision()) ? request.getDecision() : "SUGGEST_CONFIRM";
        if (!Set.of("SUGGEST_CONFIRM", "SUGGEST_REJECT").contains(decision)) {
            throw new BizException("成果建议仅支持建议确认或建议驳回");
        }
        String comment = requireActionComment(request.getComment(), "确认建议说明不能为空", 1000);
        result.setSuggestionStatus(decision);
        result.setLeaderSuggestion(comment);
        result.setSuggestedBy(user.userId());
        result.setSuggestedAt(LocalDateTime.now());
        result.setUpdatedBy(user.userId());
        resultMapper.updateById(result);
        completeObjectTodos("RESULT", id);
        createResultConfirmTodo(user, result);
        return actionResult(id, decision, "成果确认建议已提交给部门负责人。", "RESULT_CONFIRM_SUGGEST");
    }

    @Transactional
    public List<ActionResultVO> batchSubmitResultSuggestions(AuthUser user, BatchActionReqDTO request) {
        List<ActionResultVO> results = new ArrayList<>();
        for (String id : request.getIds()) {
            PerformanceActionReqDTO action = new PerformanceActionReqDTO();
            action.setDecision(request.getDecision());
            action.setComment(request.getComment());
            results.add(submitResultSuggestion(user, id, action));
        }
        return results;
    }

    public List<PlanAdjustmentItemVO> planAdjustments(AuthUser user, Long scopeOrgId, String status, String periodMonth) {
        roleGuard.requireLeaderModule(user);
        Set<Long> ownerIds = dataScopeService.leaderOwnerIds(user, scopeOrgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        YearMonth month = StringUtils.hasText(periodMonth) ? parsePeriodMonth(periodMonth) : null;
        Map<Long, SysUser> users = dataScopeService.userMap();
        return planAdjustmentMapper.selectList(new LambdaQueryWrapper<BizPlanAdjustment>()
                        .eq(BizPlanAdjustment::getDeleted, 0)
                        .in(BizPlanAdjustment::getOwnerUserId, ownerIds)
                        .eq(StringUtils.hasText(status), BizPlanAdjustment::getStatus, status)
                        .ge(month != null, BizPlanAdjustment::getCreatedAt, month == null ? null : month.atDay(1).atStartOfDay())
                        .lt(month != null, BizPlanAdjustment::getCreatedAt, month == null ? null : month.plusMonths(1).atDay(1).atStartOfDay())
                        .orderByDesc(BizPlanAdjustment::getCreatedAt)
                        .orderByDesc(BizPlanAdjustment::getId))
                .stream()
                .map(item -> toPlanAdjustment(item, users.get(item.getOwnerUserId())))
                .toList();
    }

    public PlanAdjustmentItemVO planAdjustmentDetail(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizPlanAdjustment item = requirePlanAdjustment(id);
        dataScopeService.requireLeaderOwner(user, item.getOwnerUserId());
        return toPlanAdjustment(item, dataScopeService.requireUser(item.getOwnerUserId()));
    }

    public List<ExtraMonthPlanApprovalVO> extraMonthPlanApprovals(AuthUser user, Long scopeOrgId, String status) {
        roleGuard.requireLeaderModule(user);
        Set<Long> ownerIds = dataScopeService.leaderOwnerIds(user, scopeOrgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : null;
        if (normalizedStatus != null && !Set.of("PENDING", "APPROVED", "REJECTED").contains(normalizedStatus)) {
            throw new BizException(422, "额外任务审批状态仅支持 PENDING、APPROVED、REJECTED");
        }
        List<BizMonthPlanItem> items = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getTaskType, "EXTRA")
                .eq(normalizedStatus != null, BizMonthPlanItem::getStatus, normalizedStatus)
                .orderByDesc(BizMonthPlanItem::getSubmitAt)
                .orderByDesc(BizMonthPlanItem::getId));
        Map<Long, BizMonthPlan> plans = monthPlanMap(items.stream()
                .map(BizMonthPlanItem::getMonthPlanId).distinct().toList());
        Map<Long, SysUser> users = dataScopeService.userMap();
        return items.stream()
                .filter(item -> plans.containsKey(item.getMonthPlanId()))
                .filter(item -> ownerIds.contains(plans.get(item.getMonthPlanId()).getOwnerUserId()))
                .filter(item -> user.userId().equals(dataScopeService.directLeaderId(
                        plans.get(item.getMonthPlanId()).getOwnerUserId())))
                .map(item -> toExtraMonthPlanApproval(item, plans.get(item.getMonthPlanId()),
                        users.get(plans.get(item.getMonthPlanId()).getOwnerUserId())))
                .toList();
    }

    @Transactional
    public ActionResultVO approveExtraMonthPlanItem(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizMonthPlanItem item = requireExtraMonthPlanItemForUpdate(user, id);
        String comment = StringUtils.hasText(request.getComment()) ? request.getComment().trim() : "同意纳入额外月计划";
        if (comment.length() > 500) {
            throw new BizException(422, "审批意见不能超过500个字符");
        }
        completeExtraMonthPlanApproval(user, item, "APPROVED", comment);
        return new ActionResultVO(id, "APPROVED", "额外任务已审批通过，原月计划保持有效。",
                "EXTRA_MONTH_PLAN_ITEM_APPROVE", false);
    }

    @Transactional
    public ActionResultVO rejectExtraMonthPlanItem(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizMonthPlanItem item = requireExtraMonthPlanItemForUpdate(user, id);
        String comment = requireActionComment(request.getComment(), "驳回原因不能为空", 500);
        completeExtraMonthPlanApproval(user, item, "REJECTED", comment);
        return new ActionResultVO(id, "REJECTED", "额外任务已驳回，未影响原月计划。",
                "EXTRA_MONTH_PLAN_ITEM_REJECT", false);
    }

    private void completeExtraMonthPlanApproval(AuthUser user, BizMonthPlanItem item, String status, String comment) {
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(status);
        item.setApproverId(user.userId());
        item.setApproveAt(now);
        item.setApprovalComment(comment);
        item.setUpdatedBy(user.userId());
        monthPlanItemMapper.updateById(item);
        completeObjectTodos("MONTH_PLAN_EXTRA_ITEM", String.valueOf(item.getId()));
        BizMonthPlan plan = monthPlanMapper.selectById(item.getMonthPlanId());
        if (plan != null) {
            String approved = "APPROVED".equals(status) ? "审批通过" : "审批驳回";
            messageService.createNotice(plan.getOwnerUserId(), "EXTRA_MONTH_PLAN_ITEM_APPROVAL_RESULT",
                    "额外任务" + approved,
                    user.realName() + "已处理额外任务“" + item.getTaskName() + "”，请查看审批意见。",
                    "MONTH_PLAN_EXTRA_ITEM_RESULT", String.valueOf(item.getId()),
                    "/employee/month-plans/" + plan.getId(), plan.getDeptId(), user.userId());
        }
        auditLogService.success(user, "APPROVED".equals(status)
                        ? "EXTRA_MONTH_PLAN_ITEM_APPROVE" : "EXTRA_MONTH_PLAN_ITEM_REJECT",
                "MONTH_PLAN_ITEM", item.getId(),
                "{\"status\":\"" + status + "\",\"monthPlanId\":" + item.getMonthPlanId() + "}");
    }

    private ExtraMonthPlanApprovalVO toExtraMonthPlanApproval(BizMonthPlanItem item, BizMonthPlan plan, SysUser owner) {
        return new ExtraMonthPlanApprovalVO(
                String.valueOf(item.getId()), plan.getId(), plan.getPlanMonth(), plan.getOwnerUserId(),
                owner == null ? "" : owner.getEmployeeNo(), owner == null ? "未知员工" : owner.getRealName(),
                plan.getDeptId(), dataScopeService.departmentName(plan.getDeptId()), item.getTaskName(),
                item.getTaskContent(), item.getDeliverable(), item.getDeadline(), item.getPerformanceWeight(),
                item.getStatus(), item.getSubmitAt(), item.getApproverId(), item.getApproveAt(), item.getApprovalComment());
    }

    @Transactional
    public ActionResultVO processPlanAdjustment(AuthUser user, String id, PerformanceActionReqDTO request) {
        roleGuard.requireLeaderModule(user);
        BizPlanAdjustment item = requirePlanAdjustmentForUpdate(id);
        dataScopeService.requireLeaderOwner(user, item.getOwnerUserId());
        if (!"PENDING".equals(item.getStatus())) {
            throw new BizException(409, "计划调整申请已处理，不能重复操作");
        }
        String action = StringUtils.hasText(request.getAction()) ? request.getAction() : "PAUSE";
        String status = switch (action) {
            case "CANCEL" -> "CANCELED";
            case "PAUSE" -> "PAUSED";
            default -> throw new BizException("计划调整仅支持暂停或撤销");
        };
        String comment = requireActionComment(request.getComment(), "处理说明不能为空", 1000);
        item.setStatus(status);
        item.setOperationComment(comment);
        item.setKeepEvidenceChain(true);
        item.setOperatorId(user.userId());
        item.setOperatorName(user.realName());
        item.setOperatedAt(LocalDateTime.now());
        item.setUpdatedBy(user.userId());
        planAdjustmentMapper.updateById(item);
        syncAdjustedPlanStatus(item, status, user.userId());
        completeObjectTodos("PLAN_ADJUSTMENT", id);
        messageService.createNotice(item.getOwnerUserId(), "PLAN_ADJUSTMENT_RESULT", "计划调整申请已处理",
                user.realName() + "已处理你的计划" + ("PAUSED".equals(status) ? "暂停" : "撤销")
                        + "申请，请查看处理意见。",
                "PLAN_ADJUSTMENT_RESULT", id, "/employee/month-plans/" + item.getOriginalPlanId(),
                item.getDeptId(), user.userId());
        return actionResult(id, action, "计划调整已处理并同步到员工端。", "PLAN_ADJUSTMENT_PROCESS");
    }

    public List<LedgerItemVO> teamLedgers(AuthUser user, Long scopeOrgId, String periodType,
                                           LocalDate periodStart, LocalDate periodEnd, String employeeName) {
        roleGuard.requireLeaderModule(user);
        Set<Long> ownerIds = dataScopeService.leaderOwnerIds(user, scopeOrgId);
        return buildLedgers(ownerIds, periodType, periodStart, periodEnd, employeeName);
    }

    public List<LedgerItemVO> departmentLedgers(AuthUser user, Long scopeOrgId, String periodType,
                                                 LocalDate periodStart, LocalDate periodEnd, String employeeName) {
        roleGuard.requireDepartmentModule(user);
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, scopeOrgId);
        return buildLedgers(ownerIds, periodType, periodStart, periodEnd, employeeName);
    }

    private List<LedgerItemVO> buildLedgers(Set<Long> ownerIds, String periodType,
                                             LocalDate periodStart, LocalDate periodEnd, String employeeName) {
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        PeriodRange period = periodRange(periodType, periodStart, periodEnd);
        Map<Long, SysUser> users = dataScopeService.userMap();
        Map<Long, SysDept> departments = dataScopeService.departmentMap();
        Map<Long, BizScoreRule> activeRules = activeScoreRules(period);
        List<LedgerItemVO> ledgers = new ArrayList<>();
        for (Long ownerId : ownerIds) {
            SysUser owner = users.get(ownerId);
            if (owner == null || StringUtils.hasText(employeeName) && !owner.getRealName().contains(employeeName)) {
                continue;
            }
            ledgers.add(buildLedger(owner, period, inheritedScoreRule(owner.getDeptId(), departments, activeRules)));
        }
        return ledgers.stream().sorted(Comparator.comparing(LedgerItemVO::employeeNo)).toList();
    }

    public ExportTaskVO exportTeamLedger(AuthUser user, ExportTaskCreateReqDTO request) {
        return createExportTask(user, request);
    }

    @Transactional
    public ExportTaskVO createExportTask(AuthUser user, ExportTaskCreateReqDTO request) {
        roleGuard.requireLeaderModule(user);
        List<String> formats = normalizeFormats(request.getFormats());
        String dimensionType = StringUtils.hasText(request.getDimensionType()) ? request.getDimensionType() : "SUBORDINATE_LEDGER";
        if (!EXPORT_DIMENSIONS.contains(dimensionType)) {
            throw new BizException("不支持的直属领导导出类型");
        }
        Set<Long> exportOwnerIds;
        if ("PERSON_LEDGER".equals(dimensionType)) {
            Long ownerId = parseId(request.getDimensionId(), "员工");
            dataScopeService.requireLeaderOwner(user, ownerId);
            exportOwnerIds = Set.of(ownerId);
        } else {
            Long orgId = StringUtils.hasText(request.getDimensionId())
                    ? parseId(request.getDimensionId(), "组织") : null;
            dataScopeService.requireLeaderOrg(user, orgId);
            exportOwnerIds = dataScopeService.leaderOwnerIds(user, orgId);
        }
        BizExportTask task = new BizExportTask();
        task.setId("EXP-LEADER-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
        task.setDimensionType(dimensionType);
        task.setDimensionId(request.getDimensionId());
        task.setDimensionName(exportDimensionName(dimensionType));
        task.setPeriodType(StringUtils.hasText(request.getPeriodType()) ? request.getPeriodType() : "MONTH");
        task.setPeriodStart(request.getPeriodStart());
        task.setPeriodEnd(request.getPeriodEnd());
        task.setFormats(jsonCodec.write(formats));
        task.setIncludeEvidence(Boolean.TRUE.equals(request.getIncludeEvidence()));
        task.setWatermark(StringUtils.hasText(request.getWatermark()) ? request.getWatermark() : "直属领导、组织、导出时间、周期");
        task.setIntegrityStatus("PENDING_CHECK");
        task.setMissingItems("[]");
        task.setStatus("PENDING");
        task.setSizeText("--");
        task.setRequestedBy(user.userId());
        task.setRequestedByName(user.realName());
        task.setRequestedAt(LocalDateTime.now());
        task.setDeptId(user.deptId());
        task.setDeleted(0);
        exportTaskMapper.insert(task);
        try {
            exportFileService.generate(task, exportOwnerIds);
            exportTaskMapper.updateById(task);
        } catch (RuntimeException ex) {
            exportTaskMapper.updateById(task);
            throw ex;
        }
        return toExportTask(task);
    }

    public ExportDownloadVO exportDownloadInfo(AuthUser user, String id) {
        BizExportTask task = requireExportTask(user, id);
        requireExportNotExpired(task);
        if (!"SUCCESS".equals(task.getStatus())) {
            throw new BizException(409, "导出任务未成功，暂不可下载");
        }
        return new ExportDownloadVO(task.getId(), task.getStatus(), task.getFileName(),
                "/api/leader/export-tasks/" + task.getId() + "/download", task.getExpireAt(), task.getChecksum());
    }

    public Resource downloadExport(AuthUser user, String id) {
        BizExportTask task = requireExportTask(user, id);
        requireExportNotExpired(task);
        if (!"SUCCESS".equals(task.getStatus()) || !exportFileService.verify(task)) {
            throw new BizException(409, "导出文件尚未生成或完整性校验失败");
        }
        return exportFileService.resource(task);
    }

    private DailyReviewItemVO toDailyReview(BizDayPlan plan, SysUser owner, BizMonthPlanItem item) {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(plan.getContent())) {
            missing.add("workContent");
        }
        if (item == null || !StringUtils.hasText(item.getDeliverable())) {
            missing.add("deliverable");
        }
        LocalDateTime dueAt = plan.getApprovalDueAt() != null ? plan.getApprovalDueAt()
                : plan.getSubmitAt() == null ? plan.getPlanDate().plusDays(1).atTime(18, 0) : plan.getSubmitAt().plusDays(1);
        String reviewStatus = StringUtils.hasText(plan.getReviewStatus()) ? plan.getReviewStatus()
                : "APPROVED".equals(plan.getStatus()) ? "COMMENTED" : "PENDING_COMMENT";
        return new DailyReviewItemVO(
                String.valueOf(plan.getId()),
                plan.getOwnerUserId(),
                owner == null ? "" : owner.getEmployeeNo(),
                owner == null ? "未知员工" : owner.getRealName(),
                plan.getDeptId(),
                dataScopeService.departmentName(plan.getDeptId()),
                plan.getPlanDate(),
                plan.getSubmitAt(),
                plan.getContent(),
                item == null || item.getDeliverable() == null ? "" : item.getDeliverable(),
                dueAt,
                LocalDateTime.now().isAfter(dueAt) && !"COMMENTED".equals(reviewStatus),
                missing,
                missing.isEmpty() ? defaultText(plan.getAiCheckResult(), "NORMAL") : "REQUIRED_FIELD_MISSING",
                reviewStatus,
                defaultText(plan.getRiskLevel(), "LOW"),
                plan.getApprovalComment(),
                plan.getReviewedAt()
        );
    }

    private ResultSuggestionItemVO toResultSuggestion(BizResult result, SysUser owner, BizMonthPlan plan) {
        List<EvidenceFileVO> evidences = resultEvidenceMapper.selectList(new LambdaQueryWrapper<BizResultEvidence>()
                        .eq(BizResultEvidence::getDeleted, 0)
                        .eq(BizResultEvidence::getResultId, result.getId())
                        .orderByAsc(BizResultEvidence::getId))
                .stream()
                .map(file -> new EvidenceFileVO(file.getId(), file.getFileName(), file.getFileType(),
                        defaultText(file.getStatus(), "UPLOADED"), Boolean.TRUE.equals(file.getReviewPassed())))
                .toList();
        String evidenceStatus = evidences.isEmpty() ? "MISSING" : defaultText(result.getEvidenceStatus(), "COMPLETE");
        return new ResultSuggestionItemVO(
                String.valueOf(result.getId()),
                result.getOwnerUserId(),
                owner == null ? "" : owner.getEmployeeNo(),
                owner == null ? "未知员工" : owner.getRealName(),
                result.getDeptId(),
                dataScopeService.departmentName(result.getDeptId()),
                "RES-" + result.getId(),
                result.getTitle(),
                result.getPlanType(),
                result.getPlanId(),
                plan == null ? "PLAN-" + result.getPlanId() : "MP-" + plan.getPlanMonth() + "-" + plan.getId(),
                BigDecimal.valueOf(result.getCompletionRate() == null ? 0 : result.getCompletionRate()),
                defaultText(result.getAutoLevel(), autoLevel(result.getCompletionRate())),
                evidenceStatus,
                jsonCodec.stringList(result.getIssueCodes()),
                defaultText(result.getIssueText(), evidences.isEmpty() ? "未上传成果证据" : ""),
                defaultText(result.getSuggestionStatus(), "PENDING_SUGGEST"),
                result.getLeaderSuggestion(),
                result.getStatus(),
                evidences
        );
    }

    private PlanAdjustmentItemVO toPlanAdjustment(BizPlanAdjustment item, SysUser owner) {
        return new PlanAdjustmentItemVO(
                String.valueOf(item.getId()), item.getOriginalPlanType(), item.getOriginalPlanId(), item.getOriginalPlanNo(),
                item.getOriginalWorkContent(), item.getNewPlanType(), item.getNewPlanId(), item.getNewPlanNo(),
                item.getOwnerUserId(), owner == null ? "未知员工" : owner.getRealName(), item.getAdjustmentType(),
                item.getReason(), item.getImpactText(), item.getOperationComment(), item.getStatus(), Boolean.TRUE.equals(item.getKeepEvidenceChain()),
                item.getOperatorName(), item.getOperatedAt()
        );
    }

    private LedgerItemVO buildLedger(SysUser owner, PeriodRange period, BizScoreRule scoreRule) {
        int planCount = Math.toIntExact(monthPlanMapper.selectCount(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, owner.getId())
                .ge(BizMonthPlan::getPlanMonth, period.start().toString().substring(0, 7))
                .le(BizMonthPlan::getPlanMonth, period.end().toString().substring(0, 7))));
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, owner.getId())
                .ge(BizResult::getResultDate, period.start())
                .le(BizResult::getResultDate, period.end()));
        BigDecimal average = results.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(results.stream()
                .mapToInt(item -> item.getCompletionRate() == null ? 0 : item.getCompletionRate())
                .average().orElse(0)).setScale(2, RoundingMode.HALF_UP);
        int overdue = Math.toIntExact(dayPlanMapper.selectCount(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getOwnerUserId, owner.getId())
                .ge(BizDayPlan::getPlanDate, period.start())
                .le(BizDayPlan::getPlanDate, period.end())
                .lt(BizDayPlan::getApprovalDueAt, LocalDateTime.now())
                .ne(BizDayPlan::getReviewStatus, "COMMENTED")));
        int missingEvidence = (int) results.stream().filter(item -> !"COMPLETE".equals(item.getEvidenceStatus())).count();
        int rejectCount = (int) results.stream().filter(item -> "REJECTED".equals(item.getStatus())).count();
        boolean evidenceComplete = !results.isEmpty() && missingEvidence == 0;
        boolean reviewPassed = !results.isEmpty() && results.stream().allMatch(item -> "CONFIRMED".equals(item.getStatus()));
        BigDecimal referenceScore = calculateReferenceScore(
                scoreRule, average, overdue, rejectCount, evidenceComplete, reviewPassed);
        boolean hasOpenAppeal = appealMapper.selectCount(new LambdaQueryWrapper<BizEmployeeAppeal>()
                .eq(BizEmployeeAppeal::getDeleted, 0)
                .eq(BizEmployeeAppeal::getOwnerUserId, owner.getId())
                .in(BizEmployeeAppeal::getStatus, List.of("SUBMITTED", "PROCESSING"))) > 0;
        return new LedgerItemVO(
                "LEDGER-" + owner.getId() + "-" + period.type() + "-" + period.start() + "-" + period.end(),
                owner.getId(), owner.getEmployeeNo(), owner.getRealName(),
                owner.getDeptId(), dataScopeService.departmentName(owner.getDeptId()), period.type(), period.start(), period.end(),
                planCount, results.size(), average, referenceScore, overdue, missingEvidence,
                missingEvidence == 0 ? "COMPLETE" : "INCOMPLETE", hasOpenAppeal ? "PROCESSING" : "NONE"
        );
    }

    private Map<Long, BizScoreRule> activeScoreRules(PeriodRange period) {
        Map<Long, BizScoreRule> rules = new HashMap<>();
        scoreRuleMapper.selectList(new LambdaQueryWrapper<BizScoreRule>()
                        .eq(BizScoreRule::getDeleted, 0)
                        .eq(BizScoreRule::getStatus, "ENABLED")
                        .and(wrapper -> wrapper.isNull(BizScoreRule::getEffectiveStart)
                                .or().le(BizScoreRule::getEffectiveStart, period.end()))
                        .and(wrapper -> wrapper.isNull(BizScoreRule::getEffectiveEnd)
                                .or().ge(BizScoreRule::getEffectiveEnd, period.start()))
                        .orderByDesc(BizScoreRule::getEffectiveStart)
                        .orderByDesc(BizScoreRule::getId))
                .forEach(rule -> rules.putIfAbsent(rule.getDeptId(), rule));
        return rules;
    }

    private BizScoreRule inheritedScoreRule(Long deptId, Map<Long, SysDept> departments,
                                             Map<Long, BizScoreRule> activeRules) {
        Long currentId = deptId;
        while (currentId != null && currentId != 0L) {
            BizScoreRule rule = activeRules.get(currentId);
            if (rule != null) {
                return rule;
            }
            SysDept department = departments.get(currentId);
            currentId = department == null ? null : department.getParentId();
        }
        return null;
    }

    private BigDecimal calculateReferenceScore(BizScoreRule rule, BigDecimal completionRatio,
                                                int overdueCount, int rejectCount,
                                                boolean evidenceComplete, boolean reviewPassed) {
        if (rule == null || !StringUtils.hasText(rule.getRuleJson())) {
            return completionRatio;
        }
        Object rawFactors = jsonCodec.objectMap(rule.getRuleJson()).get("factors");
        if (!(rawFactors instanceof List<?> factors) || factors.isEmpty()) {
            return completionRatio;
        }
        BigDecimal score = BigDecimal.ZERO;
        for (Object rawFactor : factors) {
            if (!(rawFactor instanceof Map<?, ?> factor) || Boolean.FALSE.equals(factor.get("enabled"))) {
                continue;
            }
            String code = String.valueOf(factor.get("code"));
            BigDecimal weight = decimalValue(factor.get("weight"), BigDecimal.ZERO);
            BigDecimal contribution = switch (code) {
                case "completion_ratio" -> completionRatio.multiply(weight)
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                case "overdue_count" -> weight.subtract(decimalValue(factor.get("penaltyPerTime"), new BigDecimal("2"))
                        .multiply(BigDecimal.valueOf(overdueCount))).max(BigDecimal.ZERO);
                case "reject_count" -> weight.subtract(decimalValue(factor.get("penaltyPerTime"), new BigDecimal("3"))
                        .multiply(BigDecimal.valueOf(rejectCount))).max(BigDecimal.ZERO);
                case "evidence_complete" -> evidenceComplete ? weight : BigDecimal.ZERO;
                case "review_passed" -> reviewPassed ? weight : BigDecimal.ZERO;
                default -> BigDecimal.ZERO;
            };
            score = score.add(contribution);
        }
        return score.max(BigDecimal.ZERO).min(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private List<LeaderDateStatusVO> leaderDateStatuses(List<DailyReviewItemVO> reviews, List<ResultSuggestionItemVO> suggestions) {
        Map<String, List<DailyReviewItemVO>> groups = new LinkedHashMap<>();
        for (DailyReviewItemVO item : reviews) {
            groups.computeIfAbsent(item.planDate() + "-" + item.orgId(), ignored -> new ArrayList<>()).add(item);
        }
        List<LeaderDateStatusVO> result = new ArrayList<>();
        for (List<DailyReviewItemVO> items : groups.values()) {
            DailyReviewItemVO first = items.get(0);
            int pending = (int) items.stream().filter(item -> "PENDING_COMMENT".equals(item.reviewStatus())).count();
            int overdue = (int) items.stream().filter(item -> Boolean.TRUE.equals(item.overdueApproval())).count();
            int pendingSuggest = (int) suggestions.stream()
                    .filter(item -> first.orgId().equals(item.orgId()) && "PENDING_SUGGEST".equals(item.suggestionStatus()))
                    .count();
            result.add(new LeaderDateStatusVO(first.planDate(), first.orgId(), first.orgName(), pending, pendingSuggest,
                    overdue, overdue > 0 ? "OVERDUE" : pending + pendingSuggest > 0 ? "FOLLOW_UP" : "NORMAL"));
        }
        return result;
    }

    private List<MetricVO> leaderMetrics(List<DailyReviewItemVO> reviews, List<ResultSuggestionItemVO> suggestions) {
        return List.of(
                new MetricVO("pending_review", "待点评日计划", (int) reviews.stream().filter(item -> "PENDING_COMMENT".equals(item.reviewStatus())).count(), "primary"),
                new MetricVO("pending_suggest", "待建议成果", (int) suggestions.stream().filter(item -> "PENDING_SUGGEST".equals(item.suggestionStatus())).count(), "success"),
                new MetricVO("overdue_unhandled", "逾期未处理", (int) reviews.stream().filter(item -> Boolean.TRUE.equals(item.overdueApproval())).count(), "danger"),
                new MetricVO("missing_required_fields", "字段缺失项", (int) reviews.stream().filter(item -> !item.missingFields().isEmpty()).count(), "warning")
        );
    }

    private void createRiskTodo(AuthUser operator, BizDayPlan plan) {
        Long receiverId = dataScopeService.departmentOwnerId(plan.getDeptId());
        if (receiverId == null) {
            return;
        }
        upsertTodo("DAY_PLAN_REVIEW", "日计划补审", operator.realName() + "标记了日计划风险", receiverId,
                "DAY_PLAN", String.valueOf(plan.getId()), plan.getApprovalDueAt(), "复核风险并通过或退回补充", "影响日计划闭环率",
                "/department/todo", plan.getDeptId(), operator.userId());
    }

    private void createResultConfirmTodo(AuthUser operator, BizResult result) {
        Long receiverId = dataScopeService.departmentOwnerId(result.getDeptId());
        if (receiverId == null) {
            return;
        }
        upsertTodo("RESULT_CONFIRM", "成果最终确认", operator.realName() + "提交了成果确认建议", receiverId,
                "RESULT", String.valueOf(result.getId()), LocalDateTime.now().plusDays(1), "完成最终确认或驳回", "影响成果闭环率",
                "/department/result-confirm", result.getDeptId(), operator.userId());
    }

    private void upsertTodo(String sceneCode, String title, String triggerText, Long receiverId, String objectType,
                            String objectId, LocalDateTime dueAt, String requirement, String impact, String routeHint,
                            Long deptId, Long createdBy) {
        BizTodo todo = todoMapper.selectOne(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, receiverId)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId)
                .ne(BizTodo::getStatus, "DONE")
                .last("LIMIT 1"));
        SysUser receiver = dataScopeService.requireUser(receiverId);
        if (todo == null) {
            todo = new BizTodo();
            todo.setSceneCode(sceneCode);
            todo.setReceiverId(receiverId);
            todo.setObjectType(objectType);
            todo.setObjectId(objectId);
            todo.setMessageType("TODO");
            todo.setStatus("UNREAD");
            todo.setRemindCount(0);
            todo.setCreatedBy(createdBy);
            todo.setDeleted(0);
        }
        todo.setTitle(title);
        todo.setTriggerText(triggerText);
        todo.setReceiverName(receiver.getRealName());
        todo.setDueAt(dueAt);
        todo.setRequirementText(requirement);
        todo.setImpactText(impact);
        todo.setRouteHint(routeHint);
        todo.setDeptId(deptId);
        todo.setUpdatedBy(createdBy);
        if (todo.getId() == null) {
            todoMapper.insert(todo);
        } else {
            todoMapper.updateById(todo);
        }
    }

    private void completeObjectTodos(String objectType, String objectId) {
        List<BizTodo> todos = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId)
                .ne(BizTodo::getStatus, "DONE"));
        for (BizTodo todo : todos) {
            todo.setStatus("DONE");
            todoMapper.updateById(todo);
        }
    }

    private void syncAdjustedPlanStatus(BizPlanAdjustment item, String status, Long operatorId) {
        if ("MONTH".equals(item.getOriginalPlanType())) {
            BizMonthPlan plan = monthPlanMapper.selectForUpdateById(item.getOriginalPlanId());
            if (plan != null) {
                plan.setStatus(status);
                plan.setUpdatedBy(operatorId);
                monthPlanMapper.updateById(plan);
            }
        } else if ("DAY".equals(item.getOriginalPlanType())) {
            BizDayPlan plan = dayPlanMapper.selectForUpdateById(item.getOriginalPlanId());
            if (plan != null) {
                plan.setStatus(status);
                plan.setUpdatedBy(operatorId);
                dayPlanMapper.updateById(plan);
            }
        }
    }

    private BizDayPlan requireLeaderDayPlanForUpdate(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizDayPlan plan = dayPlanMapper.selectForUpdateById(parseId(id, "日计划"));
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "日计划点评记录不存在");
        }
        dataScopeService.requireLeaderOwner(user, plan.getOwnerUserId());
        return plan;
    }

    private void requirePendingLeaderReview(BizDayPlan plan) {
        String reviewStatus = StringUtils.hasText(plan.getReviewStatus()) ? plan.getReviewStatus() : "PENDING_COMMENT";
        if (!"PENDING".equals(plan.getStatus()) || !"PENDING_COMMENT".equals(reviewStatus)) {
            throw new BizException(409, "日计划已完成初审，不能重复点评或标记风险");
        }
    }

    private BizResult requireLeaderResultForUpdate(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizResult result = resultMapper.selectForUpdateById(parseId(id, "成果"));
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "成果建议记录不存在");
        }
        dataScopeService.requireLeaderOwner(user, result.getOwnerUserId());
        return result;
    }

    private BizDayPlan requireDayPlan(String id) {
        BizDayPlan plan = dayPlanMapper.selectById(parseId(id, "日计划"));
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "日计划点评记录不存在");
        }
        return plan;
    }

    private BizResult requireResult(String id) {
        BizResult result = resultMapper.selectById(parseId(id, "成果"));
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "成果建议记录不存在");
        }
        return result;
    }

    private BizPlanAdjustment requirePlanAdjustment(String id) {
        BizPlanAdjustment item = planAdjustmentMapper.selectById(parseId(id, "计划调整"));
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "计划调整单不存在");
        }
        return item;
    }

    private BizPlanAdjustment requirePlanAdjustmentForUpdate(String id) {
        BizPlanAdjustment item = planAdjustmentMapper.selectForUpdateById(parseId(id, "计划调整"));
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "计划调整单不存在");
        }
        return item;
    }

    private BizMonthPlanItem requireExtraMonthPlanItemForUpdate(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizMonthPlanItem item = monthPlanItemMapper.selectForUpdateById(parseId(id, "额外任务"));
        if (item == null || Integer.valueOf(1).equals(item.getDeleted()) || !"EXTRA".equals(item.getTaskType())) {
            throw new BizException(404, "额外任务审批记录不存在");
        }
        BizMonthPlan plan = monthPlanMapper.selectById(item.getMonthPlanId());
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "额外任务所属月计划不存在");
        }
        dataScopeService.requireLeaderOwner(user, plan.getOwnerUserId());
        Long assignedLeaderId = dataScopeService.directLeaderId(plan.getOwnerUserId());
        if (!user.userId().equals(assignedLeaderId)) {
            throw new BizException(403, "只有该员工的直属领导可以审批额外任务");
        }
        if (!"PENDING".equals(item.getStatus())) {
            throw new BizException(409, "额外任务已处理，不能重复审批");
        }
        return item;
    }

    private Long parseId(String id, String objectName) {
        if (!StringUtils.hasText(id)) {
            throw new BizException(400, "请选择" + objectName);
        }
        String normalized = id.trim();
        if (!normalized.matches("\\d+")) {
            throw new BizException(400, objectName + "编号格式错误");
        }
        try {
            return Long.valueOf(normalized);
        } catch (NumberFormatException ex) {
            throw new BizException(400, objectName + "编号格式错误");
        }
    }

    private Map<Long, BizMonthPlanItem> monthItemMap(List<Long> ids) {
        Map<Long, BizMonthPlanItem> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        monthPlanItemMapper.selectBatchIds(ids).forEach(item -> result.put(item.getId(), item));
        return result;
    }

    private Map<Long, BizMonthPlan> monthPlanMap(List<Long> ids) {
        Map<Long, BizMonthPlan> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        monthPlanMapper.selectBatchIds(ids).forEach(item -> result.put(item.getId(), item));
        return result;
    }

    private PeriodRange periodRange(String periodType, LocalDate requestedStart, LocalDate requestedEnd) {
        String type = StringUtils.hasText(periodType) ? periodType.toUpperCase(Locale.ROOT) : "MONTH";
        if ((requestedStart == null) != (requestedEnd == null)) {
            throw new BizException("统计开始日期和结束日期必须同时填写");
        }
        if (requestedStart != null) {
            if (requestedStart.isAfter(requestedEnd)) {
                throw new BizException("统计开始日期不能晚于结束日期");
            }
            return new PeriodRange(type, requestedStart, requestedEnd);
        }
        LocalDate anchor = LocalDate.now();
        return switch (type) {
            case "DAY" -> new PeriodRange(type, anchor, anchor);
            case "WEEK" -> new PeriodRange(type, anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    anchor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case "QUARTER" -> {
                int startMonth = ((anchor.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(anchor.getYear(), startMonth, 1);
                yield new PeriodRange(type, start, start.plusMonths(3).minusDays(1));
            }
            case "YEAR" -> new PeriodRange(type, LocalDate.of(anchor.getYear(), 1, 1), LocalDate.of(anchor.getYear(), 12, 31));
            default -> new PeriodRange("MONTH", anchor.withDayOfMonth(1), anchor.withDayOfMonth(1).plusMonths(1).minusDays(1));
        };
    }

    private List<String> normalizeFormats(List<String> formats) {
        if (formats == null || formats.isEmpty()) {
            return List.of("PDF");
        }
        return formats.stream().map(format -> format.toUpperCase(Locale.ROOT)).map(format -> {
            String normalized = Set.of("DOC", "DOCX").contains(format) ? "WORD" : format;
            if (!EXPORT_FORMATS.contains(normalized)) {
                throw new BizException("导出格式仅支持 PDF、Word、Zip");
            }
            return normalized;
        }).distinct().toList();
    }

    private String exportDimensionName(String dimensionType) {
        return switch (dimensionType) {
            case "DAILY_REVIEW_LIST" -> "日计划点评清单";
            case "RESULT_SUGGESTION_LIST" -> "成果确认建议清单";
            case "PLAN_ADJUSTMENT_LIST" -> "计划暂停撤销记录";
            case "PERSON_LEDGER" -> "下属个人绩效资料包";
            default -> "直属下属台账";
        };
    }

    private PeriodRange workbenchPeriod(LocalDate date, String periodMonth) {
        if (date != null) {
            return new PeriodRange("DAY", date, date);
        }
        if (StringUtils.hasText(periodMonth)) {
            YearMonth month = parsePeriodMonth(periodMonth);
            return new PeriodRange("MONTH", month.atDay(1), month.atEndOfMonth());
        }
        return new PeriodRange("ALL", null, null);
    }

    private YearMonth parsePeriodMonth(String periodMonth) {
        try {
            return YearMonth.parse(periodMonth.trim());
        } catch (DateTimeException ex) {
            throw new BizException(422, "周期月份格式必须为 yyyy-MM");
        }
    }

    private BizExportTask requireExportTask(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizExportTask task = exportTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BizException(404, "导出任务不存在");
        }
        if (!user.userId().equals(task.getRequestedBy())) {
            throw new BizException(403, "无权下载该导出任务");
        }
        return task;
    }

    private void requireExportNotExpired(BizExportTask task) {
        if (task.getExpireAt() != null && task.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(410, "导出文件已过期，请重新导出");
        }
    }

    private ExportTaskVO toExportTask(BizExportTask task) {
        return new ExportTaskVO(task.getId(), task.getDimensionType(), task.getDimensionName(), task.getPeriodType(),
                task.getPeriodStart(), task.getPeriodEnd(), jsonCodec.stringList(task.getFormats()),
                Boolean.TRUE.equals(task.getIncludeEvidence()), task.getWatermark(), task.getIntegrityStatus(),
                jsonCodec.stringList(task.getMissingItems()), task.getChecksum(), task.getStatus(), task.getSizeText(),
                task.getRequestedBy(), task.getRequestedByName(), task.getRequestedAt(), task.getFinishedAt(),
                task.getExpireAt(), task.getErrorMessage());
    }

    private String autoLevel(Integer completionRate) {
        int rate = completionRate == null ? 0 : completionRate;
        return rate >= 100 ? "DONE" : rate >= 80 ? "BASIC_DONE" : "PARTIAL_DONE";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String requireActionComment(String comment, String message, int maxLength) {
        if (!StringUtils.hasText(comment)) {
            throw new BizException(422, message);
        }
        String normalized = comment.trim();
        if (normalized.length() > maxLength) {
            throw new BizException(422, "处理说明不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeRiskLevel(String riskLevel, String fallback) {
        String normalized = StringUtils.hasText(riskLevel)
                ? riskLevel.trim().toUpperCase(Locale.ROOT)
                : defaultText(fallback, "LOW").trim().toUpperCase(Locale.ROOT);
        if (!RISK_LEVELS.contains(normalized)) {
            throw new BizException(422, "风险等级仅支持 LOW、MEDIUM、HIGH");
        }
        return normalized;
    }

    private ActionResultVO actionResult(String objectId, String status, String message, String auditActionCode) {
        return new ActionResultVO(objectId, status, message, auditActionCode, true);
    }

    private record PeriodRange(String type, LocalDate start, LocalDate end) {
    }
}
