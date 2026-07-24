package com.planning.platform.performance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.performance.domain.BizAcceptanceStandard;
import com.planning.platform.performance.domain.BizDeliverableTemplate;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.domain.BizScoreRule;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.dto.AcceptanceStandardSaveReqDTO;
import com.planning.platform.performance.dto.BatchActionReqDTO;
import com.planning.platform.performance.dto.DeliverableTemplateSaveReqDTO;
import com.planning.platform.performance.dto.ExportTaskCreateReqDTO;
import com.planning.platform.performance.dto.PerformanceActionReqDTO;
import com.planning.platform.performance.dto.ScoreRuleSaveReqDTO;
import com.planning.platform.performance.dto.ScoreRuleSimulateReqDTO;
import com.planning.platform.performance.mapper.BizAcceptanceStandardMapper;
import com.planning.platform.performance.mapper.BizDeliverableTemplateMapper;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizScoreRuleMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.vo.PerformanceVO.AcceptanceStandardVO;
import com.planning.platform.performance.vo.PerformanceVO.ActionResultVO;
import com.planning.platform.performance.vo.PerformanceVO.AppealProcessVO;
import com.planning.platform.performance.vo.PerformanceVO.DeliverableTemplateVO;
import com.planning.platform.performance.vo.PerformanceVO.DepartmentDayPlanReviewVO;
import com.planning.platform.performance.vo.PerformanceVO.DepartmentDashboardVO;
import com.planning.platform.performance.vo.PerformanceVO.DepartmentSummaryVO;
import com.planning.platform.performance.vo.PerformanceVO.EvidenceFileVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportDownloadVO;
import com.planning.platform.performance.vo.PerformanceVO.ExportTaskVO;
import com.planning.platform.performance.vo.PerformanceVO.LedgerItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MetricVO;
import com.planning.platform.performance.vo.PerformanceVO.MonthPlanApprovalItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MonthPlanApprovalDetailItemVO;
import com.planning.platform.performance.vo.PerformanceVO.MonthPlanApprovalPageVO;
import com.planning.platform.performance.vo.PerformanceVO.OrgNodeVO;
import com.planning.platform.performance.vo.PerformanceVO.ResultConfirmItemVO;
import com.planning.platform.performance.vo.PerformanceVO.ScoreRuleVO;
import com.planning.platform.performance.vo.PerformanceVO.ScoreSimulationVO;
import com.planning.platform.performance.vo.PerformanceVO.TodoItemVO;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.system.domain.SysDept;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.core.io.Resource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentPerformanceService {

    private static final Set<String> EXPORT_FORMATS = Set.of("PDF", "WORD", "ZIP");
    private static final Set<String> EXPORT_DIMENSIONS = Set.of(
            "DEPARTMENT_LEDGER", "PERSON_LEDGER", "APPEAL_PACKAGE", "QUARTER_SUMMARY",
            "MONTH_PLAN_APPROVAL_LIST", "RESULT_CONFIRM_LIST"
    );
    private static final Set<String> EXPORT_PERIOD_TYPES = Set.of("DAY", "MONTH", "QUARTER", "YEAR");
    private static final Set<String> SCORE_FACTOR_CODES = Set.of(
            "completion_ratio", "overdue_count", "reject_count", "evidence_complete", "review_passed"
    );
    private static final Set<String> TEMPLATE_EVIDENCE_TYPES = Set.of(
            "DOCUMENT", "SPREADSHEET", "IMAGE", "FILE"
    );
    private static final Set<String> TEMPLATE_SCENES = Set.of("MONTH_PLAN", "DAY_PLAN", "RESULT");

    private final PerformanceRoleGuard roleGuard;
    private final PerformanceDataScopeService dataScopeService;
    private final PerformanceJsonCodec jsonCodec;
    private final LeaderPerformanceService leaderPerformanceService;
    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper resultEvidenceMapper;
    private final BizEmployeeAppealMapper appealMapper;
    private final BizTodoMapper todoMapper;
    private final BizDeliverableTemplateMapper templateMapper;
    private final BizAcceptanceStandardMapper acceptanceStandardMapper;
    private final BizScoreRuleMapper scoreRuleMapper;
    private final BizExportTaskMapper exportTaskMapper;
    private final ExportFileService exportFileService;
    private final ExportTaskWorker exportTaskWorker;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final UserMessageService messageService;

    public List<OrgNodeVO> orgTree(AuthUser user) {
        roleGuard.requireDepartmentModule(user);
        return dataScopeService.orgTree(user, true);
    }

    public DepartmentDashboardVO dashboard(AuthUser user, Long orgId, String periodType, String periodMonth) {
        roleGuard.requireDepartmentModule(user);
        YearMonth anchorMonth = StringUtils.hasText(periodMonth) ? parsePeriodMonth(periodMonth) : YearMonth.now();
        DashboardPeriod period = dashboardPeriod(periodType, anchorMonth);
        List<MonthPlanApprovalItemVO> plans = dashboardMonthPlans(user, orgId, period);
        List<ResultConfirmItemVO> results = dashboardResults(user, orgId, period);
        List<TodoItemVO> todoItems = todos(user, null, null);
        return new DepartmentDashboardVO(
                departmentMetrics(user, plans, results),
                departmentSummaries(user, orgId, period, plans, results),
                todoItems.stream().filter(item -> !"DONE".equals(item.status())).limit(3).toList()
        );
    }

    private List<MonthPlanApprovalItemVO> dashboardMonthPlans(AuthUser user, Long orgId, DashboardPeriod period) {
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, orgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SysUser> users = dataScopeService.userMap();
        return monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                        .eq(BizMonthPlan::getDeleted, 0)
                        .in(BizMonthPlan::getOwnerUserId, ownerIds)
                        .ne(BizMonthPlan::getStatus, "DRAFT")
                        .ge(BizMonthPlan::getPlanMonth, period.startMonth().toString())
                        .le(BizMonthPlan::getPlanMonth, period.endMonth().toString())
                        .orderByDesc(BizMonthPlan::getPlanMonth)
                        .orderByDesc(BizMonthPlan::getSubmitAt)
                        .orderByDesc(BizMonthPlan::getId))
                .stream()
                .map(plan -> toMonthPlanApproval(plan, users.get(plan.getOwnerUserId())))
                .toList();
    }

    private List<ResultConfirmItemVO> dashboardResults(AuthUser user, Long orgId, DashboardPeriod period) {
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, orgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .in(BizResult::getOwnerUserId, ownerIds)
                .ne(BizResult::getStatus, "DRAFT")
                .orderByDesc(BizResult::getSubmitAt)
                .orderByDesc(BizResult::getId));
        Map<Long, SysUser> users = dataScopeService.userMap();
        Map<Long, BizMonthPlan> plans = monthPlanMap(results);
        return results.stream()
                .filter(result -> matchesResultPeriod(result, plans.get(result.getPlanId()), period))
                .map(result -> toResultConfirm(result, users.get(result.getOwnerUserId()), plans.get(result.getPlanId())))
                .toList();
    }

    public List<MonthPlanApprovalItemVO> monthPlanApprovals(AuthUser user, Integer planYear, Integer planMonth,
                                                            Long orgId, String status, String keyword) {
        roleGuard.requireDepartmentModule(user);
        if (planMonth != null && (planMonth < 1 || planMonth > 12)) {
            throw new BizException(422, "计划月份必须在 1 到 12 之间");
        }
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, orgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        String planMonthText = planYear != null && planMonth != null ? "%04d-%02d".formatted(planYear, planMonth) : null;
        List<BizMonthPlan> plans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .in(BizMonthPlan::getOwnerUserId, ownerIds)
                .ne(BizMonthPlan::getStatus, "DRAFT")
                .eq(StringUtils.hasText(planMonthText), BizMonthPlan::getPlanMonth, planMonthText)
                .orderByDesc(BizMonthPlan::getSubmitAt)
                .orderByDesc(BizMonthPlan::getId));
        Map<Long, SysUser> users = dataScopeService.userMap();
        return plans.stream()
                .map(plan -> toMonthPlanApproval(plan, users.get(plan.getOwnerUserId())))
                .filter(item -> planYear == null || planYear.equals(item.planYear()))
                .filter(item -> planMonth == null || planMonth.equals(item.planMonth()))
                .filter(item -> !StringUtils.hasText(status) || status.equals(item.status()))
                .filter(item -> !StringUtils.hasText(keyword)
                        || item.employeeName().contains(keyword)
                        || item.workContent().contains(keyword)
                        || item.planNo().contains(keyword))
                .toList();
    }

    public MonthPlanApprovalItemVO monthPlanApprovalDetail(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizMonthPlan plan = requireMonthPlan(id);
        dataScopeService.requireDepartmentOwner(user, plan.getOwnerUserId());
        return toMonthPlanApproval(plan, dataScopeService.requireUser(plan.getOwnerUserId()));
    }

    public List<MonthPlanApprovalItemVO> leaderMonthPlanApprovals(AuthUser user, Integer planYear, Integer planMonth,
                                                                  Long orgId, String status, String keyword) {
        roleGuard.requireLeaderModule(user);
        if (planMonth != null && (planMonth < 1 || planMonth > 12)) {
            throw new BizException(422, "计划月份必须在 1 到 12 之间");
        }
        Set<Long> ownerIds = directLeaderOwnerIds(user, orgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        String planMonthText = planYear != null && planMonth != null ? "%04d-%02d".formatted(planYear, planMonth) : null;
        Map<Long, SysUser> users = dataScopeService.userMap();
        return monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                        .eq(BizMonthPlan::getDeleted, 0)
                        .in(BizMonthPlan::getOwnerUserId, ownerIds)
                        .ne(BizMonthPlan::getStatus, "DRAFT")
                        .eq(StringUtils.hasText(planMonthText), BizMonthPlan::getPlanMonth, planMonthText)
                        .orderByDesc(BizMonthPlan::getSubmitAt)
                        .orderByDesc(BizMonthPlan::getId)).stream()
                .map(plan -> toMonthPlanApproval(plan, users.get(plan.getOwnerUserId())))
                .filter(item -> planYear == null || planYear.equals(item.planYear()))
                .filter(item -> planMonth == null || planMonth.equals(item.planMonth()))
                .filter(item -> !StringUtils.hasText(status) || status.equals(item.status()))
                .filter(item -> !StringUtils.hasText(keyword)
                        || item.employeeName().contains(keyword)
                        || item.workContent().contains(keyword)
                        || item.planNo().contains(keyword))
                .toList();
    }

    public MonthPlanApprovalPageVO leaderMonthPlanApprovalsPage(AuthUser user, Integer planYear, Integer planMonth,
                                                                 Long orgId, String status, String keyword,
                                                                 Integer pageNo, Integer pageSize) {
        int normalizedPageNo = pageNo == null ? 1 : pageNo;
        int normalizedPageSize = pageSize == null ? 10 : pageSize;
        if (normalizedPageNo < 1) {
            throw new BizException(422, "页码必须大于等于1");
        }
        if (normalizedPageSize < 1 || normalizedPageSize > 100) {
            throw new BizException(422, "每页数量必须在1到100之间");
        }
        List<MonthPlanApprovalItemVO> all = leaderMonthPlanApprovals(user, planYear, planMonth, orgId, status, keyword);
        int fromIndex = Math.min((normalizedPageNo - 1) * normalizedPageSize, all.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, all.size());
        return new MonthPlanApprovalPageVO(all.subList(fromIndex, toIndex), all.size(), normalizedPageNo, normalizedPageSize);
    }

    public MonthPlanApprovalItemVO leaderMonthPlanApprovalDetail(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizMonthPlan plan = requireMonthPlan(id);
        requireDirectLeader(user, plan);
        return toMonthPlanApproval(plan, dataScopeService.requireUser(plan.getOwnerUserId()));
    }

    @Transactional
    public ActionResultVO approveMonthPlan(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizMonthPlan plan = requireLeaderMonthPlanForUpdate(user, id);
        requirePendingMonthPlanApproval(plan);
        MonthPlanApprovalItemVO item = toMonthPlanApproval(plan, dataScopeService.requireUser(plan.getOwnerUserId()));
        if (!item.missingFields().isEmpty()) {
            throw new BizException(422, "必要字段缺失，不能直接审批通过");
        }
        plan.setStatus("APPROVED");
        plan.setApproverId(user.userId());
        plan.setApproveAt(LocalDateTime.now());
        plan.setApprovalComment(requireActionComment(request.getComment(), "审批意见不能为空", 500));
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        syncMonthPlanItems(plan, user.userId());
        completeObjectTodos("MONTH_PLAN", id);
        createMonthPlanResultNotification(plan, user);
        auditLogService.success(user, "MONTH_PLAN_APPROVE", "MONTH_PLAN", plan.getId(),
                "{\"status\":\"APPROVED\"}");
        return actionResult(id, "APPROVED", "月计划已审批通过，并已同步到员工端。", "MONTH_PLAN_APPROVE");
    }

    @Transactional
    public ActionResultVO rejectMonthPlan(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizMonthPlan plan = requireLeaderMonthPlanForUpdate(user, id);
        requirePendingMonthPlanApproval(plan);
        plan.setStatus("REJECTED");
        plan.setApproverId(user.userId());
        plan.setApproveAt(LocalDateTime.now());
        plan.setApprovalComment(requireActionComment(request.getComment(), "驳回原因不能为空", 500));
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        syncMonthPlanItems(plan, user.userId());
        completeObjectTodos("MONTH_PLAN", id);
        createMonthPlanResultNotification(plan, user);
        auditLogService.success(user, "MONTH_PLAN_REJECT", "MONTH_PLAN", plan.getId(),
                "{\"status\":\"REJECTED\"}");
        return actionResult(id, "REJECTED", "月计划已驳回，并已同步到员工端。", "MONTH_PLAN_REJECT");
    }

    @Transactional
    public List<ActionResultVO> batchApproveMonthPlans(AuthUser user, BatchActionReqDTO request) {
        List<ActionResultVO> results = new ArrayList<>();
        for (String id : request.getIds()) {
            PerformanceActionReqDTO action = new PerformanceActionReqDTO();
            action.setComment(request.getComment());
            results.add(approveMonthPlan(user, id, action));
        }
        return results;
    }

    @Transactional
    public List<ActionResultVO> batchRejectMonthPlans(AuthUser user, BatchActionReqDTO request) {
        List<ActionResultVO> results = new ArrayList<>();
        for (String id : request.getIds()) {
            PerformanceActionReqDTO action = new PerformanceActionReqDTO();
            action.setComment(request.getComment());
            results.add(rejectMonthPlan(user, id, action));
        }
        return results;
    }

    public List<ResultConfirmItemVO> resultConfirms(AuthUser user, Long orgId, String periodMonth,
                                                    String confirmStatus, String keyword) {
        roleGuard.requireDepartmentModule(user);
        String normalizedPeriodMonth = StringUtils.hasText(periodMonth)
                ? parsePeriodMonth(periodMonth).toString() : null;
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, orgId);
        if (ownerIds.isEmpty()) {
            return List.of();
        }
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .in(BizResult::getOwnerUserId, ownerIds)
                .ne(BizResult::getStatus, "DRAFT")
                .orderByDesc(BizResult::getSubmitAt)
                .orderByDesc(BizResult::getId));
        Map<Long, SysUser> users = dataScopeService.userMap();
        Map<Long, BizMonthPlan> plans = monthPlanMap(results);
        return results.stream()
                .filter(result -> matchesResultMonth(result, plans.get(result.getPlanId()), normalizedPeriodMonth))
                .map(result -> toResultConfirm(result, users.get(result.getOwnerUserId()), plans.get(result.getPlanId())))
                .filter(item -> !StringUtils.hasText(confirmStatus) || confirmStatus.equals(item.confirmStatus()))
                .filter(item -> !StringUtils.hasText(keyword)
                        || item.employeeName().contains(keyword)
                        || item.resultTitle().contains(keyword)
                        || item.resultNo().contains(keyword))
                .toList();
    }

    public MonthPlanApprovalPageVO monthPlanApprovalsPage(AuthUser user, Integer planYear, Integer planMonth,
                                                           Long orgId, String status, String keyword,
                                                           Integer pageNo, Integer pageSize) {
        roleGuard.requireDepartmentModule(user);
        if (planMonth != null && (planMonth < 1 || planMonth > 12)) {
            throw new BizException(422, "计划月份必须在 1 到 12 之间");
        }
        int normalizedPageNo = pageNo == null ? 1 : pageNo;
        int normalizedPageSize = pageSize == null ? 10 : pageSize;
        if (normalizedPageNo < 1) {
            throw new BizException(422, "页码必须大于等于1");
        }
        if (normalizedPageSize < 1 || normalizedPageSize > 100) {
            throw new BizException(422, "每页数量必须在1到100之间");
        }
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, orgId);
        if (ownerIds.isEmpty()) {
            return new MonthPlanApprovalPageVO(List.of(), 0, normalizedPageNo, normalizedPageSize);
        }

        Map<Long, SysUser> users = dataScopeService.userMap();
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Set<Long> keywordOwnerIds = new java.util.HashSet<>();
        if (StringUtils.hasText(normalizedKeyword)) {
            users.values().stream()
                    .filter(candidate -> ownerIds.contains(candidate.getId()))
                    .filter(candidate -> StringUtils.hasText(candidate.getRealName())
                            && candidate.getRealName().contains(normalizedKeyword))
                    .forEach(candidate -> keywordOwnerIds.add(candidate.getId()));
        }
        String storageStatus = approvalStorageStatus(status);
        if ("__NO_MATCH__".equals(storageStatus)) {
            return new MonthPlanApprovalPageVO(List.of(), 0, normalizedPageNo, normalizedPageSize);
        }

        LambdaQueryWrapper<BizMonthPlan> query = new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .in(BizMonthPlan::getOwnerUserId, ownerIds)
                .ne(BizMonthPlan::getStatus, "DRAFT")
                .likeRight(planYear != null, BizMonthPlan::getPlanMonth, planYear == null ? null : planYear + "-")
                .apply(planMonth != null, "SUBSTRING(plan_month, 6, 2) = {0}",
                        planMonth == null ? null : String.format("%02d", planMonth))
                .eq(StringUtils.hasText(storageStatus), BizMonthPlan::getStatus, storageStatus);
        if (StringUtils.hasText(normalizedKeyword)) {
            query.and(wrapper -> {
                wrapper.like(BizMonthPlan::getTitle, normalizedKeyword)
                        .or()
                        .like(BizMonthPlan::getContent, normalizedKeyword);
                if (!keywordOwnerIds.isEmpty()) {
                    wrapper.or().in(BizMonthPlan::getOwnerUserId, keywordOwnerIds);
                }
            });
        }

        long total = monthPlanMapper.selectCount(query);
        if (total == 0) {
            return new MonthPlanApprovalPageVO(List.of(), 0, normalizedPageNo, normalizedPageSize);
        }
        long offset = (long) (normalizedPageNo - 1) * normalizedPageSize;
        query.orderByDesc(BizMonthPlan::getSubmitAt)
                .orderByDesc(BizMonthPlan::getId)
                .last("LIMIT " + normalizedPageSize + " OFFSET " + offset);
        List<MonthPlanApprovalItemVO> items = monthPlanMapper.selectList(query).stream()
                .map(plan -> toMonthPlanApproval(plan, users.get(plan.getOwnerUserId())))
                .toList();
        return new MonthPlanApprovalPageVO(items, total, normalizedPageNo, normalizedPageSize);
    }

    private boolean matchesResultMonth(BizResult result, BizMonthPlan plan, String periodMonth) {
        if (!StringUtils.hasText(periodMonth)) {
            return true;
        }
        if (plan != null && periodMonth.equals(plan.getPlanMonth())) {
            return true;
        }
        LocalDateTime referenceTime = result.getSubmitAt() != null ? result.getSubmitAt() : result.getCreatedAt();
        return referenceTime != null && periodMonth.equals(YearMonth.from(referenceTime).toString());
    }

    private DashboardPeriod dashboardPeriod(String periodType, YearMonth anchorMonth) {
        String normalized = StringUtils.hasText(periodType) ? periodType.toUpperCase(Locale.ROOT) : "MONTH";
        return switch (normalized) {
            case "MONTH" -> new DashboardPeriod(anchorMonth, anchorMonth);
            case "QUARTER" -> {
                int firstMonth = ((anchorMonth.getMonthValue() - 1) / 3) * 3 + 1;
                YearMonth start = YearMonth.of(anchorMonth.getYear(), firstMonth);
                yield new DashboardPeriod(start, start.plusMonths(2));
            }
            case "YEAR" -> new DashboardPeriod(
                    YearMonth.of(anchorMonth.getYear(), 1),
                    YearMonth.of(anchorMonth.getYear(), 12)
            );
            default -> throw new BizException(400, "部门总览周期仅支持月度、季度或年度");
        };
    }

    private YearMonth parsePeriodMonth(String periodMonth) {
        try {
            return YearMonth.parse(periodMonth.trim());
        } catch (DateTimeException ex) {
            throw new BizException(422, "周期月份格式必须为 yyyy-MM");
        }
    }

    private boolean matchesResultPeriod(BizResult result, BizMonthPlan plan, DashboardPeriod period) {
        YearMonth resultMonth = null;
        if (plan != null && StringUtils.hasText(plan.getPlanMonth())) {
            resultMonth = YearMonth.parse(plan.getPlanMonth());
        } else {
            LocalDateTime referenceTime = result.getSubmitAt() != null ? result.getSubmitAt() : result.getCreatedAt();
            if (referenceTime != null) {
                resultMonth = YearMonth.from(referenceTime);
            }
        }
        return resultMonth != null
                && !resultMonth.isBefore(period.startMonth())
                && !resultMonth.isAfter(period.endMonth());
    }

    public ResultConfirmItemVO resultConfirmDetail(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizResult result = requireResult(id);
        dataScopeService.requireDepartmentOwner(user, result.getOwnerUserId());
        SysUser owner = dataScopeService.requireUser(result.getOwnerUserId());
        BizMonthPlan plan = result.getPlanId() == null ? null : monthPlanMapper.selectById(result.getPlanId());
        return toResultConfirm(result, owner, plan);
    }

    @Transactional
    public ActionResultVO confirmResult(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizResult result = requireDepartmentResultForUpdate(user, id);
        requirePendingFinalConfirmation(result);
        ResultConfirmItemVO item = toResultConfirm(result, dataScopeService.requireUser(result.getOwnerUserId()),
                result.getPlanId() == null ? null : monthPlanMapper.selectById(result.getPlanId()));
        if ("BLOCKED".equals(item.confirmStatus())) {
            throw new BizException(422, "成果证据或直属领导建议未满足最终确认条件");
        }
        if (!StringUtils.hasText(request.getAuthPassword())) {
            throw new BizException(422, "请输入当前登录密码完成强认证");
        }
        SysUser confirmer = dataScopeService.requireUser(user.userId());
        if (!StringUtils.hasText(confirmer.getPasswordHash())
                || !passwordEncoder.matches(request.getAuthPassword(), confirmer.getPasswordHash())) {
            throw new BizException(422, "当前登录密码不正确，成果未确认");
        }
        String comment = requireActionComment(request.getComment(), "确认意见不能为空", 500);
        result.setStatus("CONFIRMED");
        result.setConfirmerId(user.userId());
        result.setConfirmAt(LocalDateTime.now());
        result.setConfirmComment(comment);
        result.setVerifyRecordId("PASSWORD_REAUTH:" + UUID.randomUUID());
        result.setUpdatedBy(user.userId());
        resultMapper.updateById(result);
        markEvidenceReviewed(result.getId(), user.userId());
        completeObjectTodos("RESULT", id);
        notifyResultOutcome(user, result, true);
        return actionResult(id, "CONFIRMED", "成果已最终确认并同步到员工台账。", "RESULT_FINAL_CONFIRM");
    }

    @Transactional
    public ActionResultVO rejectResult(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizResult result = requireDepartmentResultForUpdate(user, id);
        requirePendingFinalConfirmation(result);
        String comment = requireActionComment(request.getComment(), "驳回原因不能为空", 500);
        result.setStatus("REJECTED");
        result.setConfirmerId(user.userId());
        result.setConfirmAt(LocalDateTime.now());
        result.setConfirmComment(comment);
        result.setUpdatedBy(user.userId());
        resultMapper.updateById(result);
        completeObjectTodos("RESULT", id);
        notifyResultOutcome(user, result, false);
        return actionResult(id, "REJECTED", "成果已驳回，并已同步到员工端。", "RESULT_FINAL_REJECT");
    }

    private void requirePendingFinalConfirmation(BizResult result) {
        if (!"PENDING".equals(result.getStatus())) {
            throw new BizException(409, "成果已完成最终处理，不能重复确认或驳回");
        }
    }

    public List<TodoItemVO> todos(AuthUser user, String sceneCode, String status) {
        roleGuard.requireDepartmentModule(user);
        List<BizTodo> todos = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(!isSuperAdmin(user), BizTodo::getReceiverId, user.userId())
                .eq(StringUtils.hasText(sceneCode), BizTodo::getSceneCode, sceneCode)
                .eq(StringUtils.hasText(status), BizTodo::getStatus, status)
                .orderByAsc(BizTodo::getStatus)
                .orderByAsc(BizTodo::getDueAt)
                .orderByDesc(BizTodo::getId));
        return todos.stream().map(this::toTodo).toList();
    }

    public DepartmentDayPlanReviewVO dayPlanReviewDetail(AuthUser user, String id) {
        BizDayPlan plan = requireDepartmentDayPlan(user, id);
        return toDepartmentDayPlanReview(plan);
    }

    @Transactional
    public ActionResultVO approveDayPlanReview(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizDayPlan plan = requireDepartmentDayPlanForUpdate(user, id);
        requirePendingDayPlanReview(plan);
        DepartmentDayPlanReviewVO detail = toDepartmentDayPlanReview(plan);
        if (!detail.missingFields().isEmpty()) {
            throw new BizException(422, "日计划必要字段缺失，不能复核通过");
        }
        LocalDateTime now = LocalDateTime.now();
        plan.setStatus("APPROVED");
        plan.setReviewStatus("RISK_RESOLVED");
        plan.setApproverId(user.userId());
        plan.setApproveAt(now);
        plan.setDepartmentReviewComment(optionalActionComment(request.getComment(), "部门复核通过", 500));
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        completeObjectTodos("DAY_PLAN", id);
        notifyDayPlanDepartmentOutcome(user, plan, true);
        return actionResult(id, "RISK_RESOLVED", "日计划风险已复核通过，并已同步到员工端。", "DAY_PLAN_DEPARTMENT_APPROVE");
    }

    @Transactional
    public ActionResultVO rejectDayPlanReview(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizDayPlan plan = requireDepartmentDayPlanForUpdate(user, id);
        requirePendingDayPlanReview(plan);
        String comment = requireActionComment(request.getComment(), "请填写退回补充原因", 500);
        LocalDateTime now = LocalDateTime.now();
        plan.setStatus("REJECTED");
        plan.setReviewStatus("SUPPLEMENT_REQUIRED");
        plan.setApproverId(user.userId());
        plan.setApproveAt(now);
        plan.setDepartmentReviewComment(comment);
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        completeObjectTodos("DAY_PLAN", id);
        notifyDayPlanDepartmentOutcome(user, plan, false);
        return actionResult(id, "SUPPLEMENT_REQUIRED", "日计划已退回员工补充，并已同步到员工端。", "DAY_PLAN_DEPARTMENT_REJECT");
    }

    @Transactional
    public ActionResultVO readTodo(AuthUser user, String id) {
        BizTodo todo = requireTodoForUpdate(user, id);
        if ("UNREAD".equals(todo.getStatus())) {
            todo.setStatus("READ");
            todo.setReadAt(LocalDateTime.now());
            todo.setUpdatedBy(user.userId());
            todoMapper.updateById(todo);
        }
        return actionResult(id, todo.getStatus(), "待办已进入处理状态。", "NOTIFICATION_READ");
    }

    @Transactional
    public ActionResultVO remindTodo(AuthUser user, String id) {
        BizTodo todo = requireTodoForUpdate(user, id);
        requireOpenTodo(todo);
        todo.setStatus("READ");
        todo.setRemindCount((todo.getRemindCount() == null ? 0 : todo.getRemindCount()) + 1);
        todo.setUpdatedBy(user.userId());
        todoMapper.updateById(todo);
        return actionResult(id, "REMIND_RECORDED", "催办记录已保存。", "NOTIFICATION_REMIND");
    }

    @Transactional
    public ActionResultVO escalateTodo(AuthUser user, String id) {
        BizTodo todo = requireTodoForUpdate(user, id);
        requireOpenTodo(todo);
        String impactText = defaultText(todo.getImpactText(), "");
        if (impactText.contains("已标记升级处理") || impactText.contains("已升级处理")) {
            throw new BizException(409, "该待办已标记升级处理，不能重复操作");
        }
        todo.setStatus("READ");
        todo.setImpactText(impactText + (StringUtils.hasText(impactText) ? "；" : "") + "已标记升级处理");
        todo.setUpdatedBy(user.userId());
        todoMapper.updateById(todo);
        return actionResult(id, "ESCALATED", "待办已标记升级处理。", "NOTIFICATION_ESCALATE");
    }

    @Transactional
    public ActionResultVO doneTodo(AuthUser user, String id) {
        BizTodo todo = requireTodoForUpdate(user, id);
        if (!"EXPORT_TASK".equals(todo.getObjectType())) {
            throw new BizException(409, "业务待办必须在对应处理页面完成，不能直接标记完成");
        }
        todo.setStatus("DONE");
        todo.setUpdatedBy(user.userId());
        todoMapper.updateById(todo);
        return actionResult(id, "DONE", "待办已标记处理完成。", "NOTIFICATION_DONE");
    }

    @Transactional
    public List<ActionResultVO> batchTodoAction(AuthUser user, BatchActionReqDTO request, String auditActionCode) {
        List<ActionResultVO> results = new ArrayList<>();
        for (String id : request.getIds()) {
            results.add("NOTIFICATION_BATCH_ESCALATE".equals(auditActionCode) ? escalateTodo(user, id) : remindTodo(user, id));
        }
        return results;
    }

    public AppealProcessVO appealDetail(AuthUser user, String id) {
        BizEmployeeAppeal appeal = requireDepartmentAppeal(user, id);
        return toAppealProcess(appeal);
    }

    @Transactional
    public ActionResultVO acceptAppeal(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizEmployeeAppeal appeal = requireDepartmentAppealForUpdate(user, id);
        if (!"SUBMITTED".equals(appeal.getStatus())) {
            throw new BizException("只有已提交的申诉可以受理");
        }
        appeal.setStatus("PROCESSING");
        appeal.setHandlerId(user.userId());
        if (StringUtils.hasText(request.getComment())) {
            appeal.setHandleComment(requireActionComment(request.getComment(), "申诉受理说明不能为空", 500));
        }
        appeal.setUpdatedBy(user.userId());
        appealMapper.updateById(appeal);
        messageService.createNotice(appeal.getOwnerUserId(), "APPEAL_STATUS_RESULT", "申诉已受理",
                user.realName() + "已受理你的申诉“" + appeal.getTitle() + "”，请关注后续处理。",
                "APPEAL_RESULT", id, "/employee/appeals", appeal.getDeptId(), user.userId());
        return actionResult(id, "PROCESSING", "申诉已受理，员工端状态已更新。", "APPEAL_ACCEPT");
    }

    @Transactional
    public ActionResultVO resolveAppeal(AuthUser user, String id, PerformanceActionReqDTO request) {
        BizEmployeeAppeal appeal = requireDepartmentAppealForUpdate(user, id);
        if (!Set.of("SUBMITTED", "PROCESSING").contains(appeal.getStatus())) {
            throw new BizException("当前申诉状态不能提交处理意见");
        }
        String comment = requireActionComment(request.getComment(), "请填写申诉处理意见", 500);
        String decision = StringUtils.hasText(request.getDecision())
                ? request.getDecision().trim().toUpperCase(Locale.ROOT)
                : "RESOLVED";
        if (!Set.of("ACCEPT", "MAINTAIN", "RESOLVED").contains(decision)) {
            throw new BizException("申诉处理结论仅支持接受、维持原结果或处理完成");
        }
        String decisionText = "ACCEPT".equals(decision) ? "接受申诉"
                : "MAINTAIN".equals(decision) ? "维持原结果" : "处理完成";
        appeal.setStatus("RESOLVED");
        appeal.setHandlerId(user.userId());
        String handleComment = decisionText + "：" + comment;
        if (handleComment.length() > 500) {
            throw new BizException(422, "申诉处理意见不能超过 500 个字符（含处理结论）");
        }
        appeal.setHandleComment(handleComment);
        appeal.setHandledAt(LocalDateTime.now());
        appeal.setUpdatedBy(user.userId());
        appealMapper.updateById(appeal);
        completeObjectTodos("APPEAL", id);
        messageService.createNotice(appeal.getOwnerUserId(), "APPEAL_STATUS_RESULT", "申诉处理完成",
                user.realName() + "已完成申诉“" + appeal.getTitle() + "”的处理，请查看处理结论。",
                "APPEAL_RESULT", id, "/employee/appeals", appeal.getDeptId(), user.userId());
        return actionResult(id, "RESOLVED", "申诉处理意见已提交并同步到员工端。", "APPEAL_RESOLVE");
    }

    public List<DeliverableTemplateVO> templates(AuthUser user, Long orgId, String status, String keyword) {
        roleGuard.requireDepartmentModule(user);
        return templateMapper.selectList(new LambdaQueryWrapper<BizDeliverableTemplate>()
                        .eq(BizDeliverableTemplate::getDeleted, 0)
                        .eq(orgId != null, BizDeliverableTemplate::getDeptId, orgId)
                        .eq(StringUtils.hasText(status), BizDeliverableTemplate::getStatus, status)
                        .like(StringUtils.hasText(keyword), BizDeliverableTemplate::getTemplateName, keyword)
                        .orderByDesc(BizDeliverableTemplate::getUpdatedAt)
                        .orderByDesc(BizDeliverableTemplate::getId))
                .stream()
                .filter(item -> canManageOrg(user, item.getDeptId()))
                .map(this::toTemplate)
                .toList();
    }

    @Transactional
    public DeliverableTemplateVO saveTemplate(AuthUser user, Long id, DeliverableTemplateSaveReqDTO request) {
        roleGuard.requireDepartmentModule(user);
        requireManageOrg(user, request.getOrgId());
        BizDeliverableTemplate item = id == null ? new BizDeliverableTemplate() : requireTemplate(id);
        if (id != null) {
            requireManageOrg(user, item.getDeptId());
        }
        if (id == null) {
            item.setVersionNo("v1");
            item.setStatus("ENABLED");
            item.setReferenceCount(0);
            item.setCreatedBy(user.userId());
            item.setDeleted(0);
        } else {
            item.setVersionNo(nextVersion(item.getVersionNo()));
        }
        item.setDeptId(request.getOrgId());
        item.setTemplateName(request.getTemplateName().trim());
        item.setEvidenceType(normalizeTemplateEvidenceType(request.getEvidenceType()));
        item.setRequiredFlag(Boolean.TRUE.equals(request.getRequired()));
        item.setAppliesTo(normalizeTemplateAppliesTo(request.getAppliesTo()));
        item.setDescription(trimToNull(request.getDescription()));
        item.setUpdatedBy(user.userId());
        if (id == null) {
            templateMapper.insert(item);
        } else {
            templateMapper.updateById(item);
        }
        return toTemplate(item);
    }

    @Transactional
    public ActionResultVO toggleTemplate(AuthUser user, Long id, boolean enabled) {
        roleGuard.requireDepartmentModule(user);
        BizDeliverableTemplate item = requireTemplate(id);
        requireManageOrg(user, item.getDeptId());
        item.setStatus(enabled ? "ENABLED" : "DISABLED");
        item.setUpdatedBy(user.userId());
        templateMapper.updateById(item);
        return actionResult(String.valueOf(id), item.getStatus(), "交付物模板状态已更新。", "DELIVERABLE_TEMPLATE_TOGGLE");
    }

    public List<AcceptanceStandardVO> acceptanceStandards(AuthUser user, Long templateId, String status) {
        roleGuard.requireDepartmentModule(user);
        Map<Long, BizDeliverableTemplate> templates = templateMap();
        return acceptanceStandardMapper.selectList(new LambdaQueryWrapper<BizAcceptanceStandard>()
                        .eq(BizAcceptanceStandard::getDeleted, 0)
                        .eq(templateId != null, BizAcceptanceStandard::getTemplateId, templateId)
                        .eq(StringUtils.hasText(status), BizAcceptanceStandard::getStatus, status)
                        .orderByDesc(BizAcceptanceStandard::getUpdatedAt)
                        .orderByDesc(BizAcceptanceStandard::getId))
                .stream()
                .filter(item -> templates.containsKey(item.getTemplateId()) && canManageOrg(user, templates.get(item.getTemplateId()).getDeptId()))
                .map(item -> toAcceptanceStandard(item, templates.get(item.getTemplateId())))
                .toList();
    }

    @Transactional
    public AcceptanceStandardVO saveAcceptanceStandard(AuthUser user, Long id, AcceptanceStandardSaveReqDTO request) {
        roleGuard.requireDepartmentModule(user);
        BizDeliverableTemplate template = requireTemplate(request.getTemplateId());
        requireManageOrg(user, template.getDeptId());
        BizAcceptanceStandard item = id == null ? new BizAcceptanceStandard() : requireAcceptanceStandard(id);
        if (id != null) {
            BizDeliverableTemplate existingTemplate = requireTemplate(item.getTemplateId());
            requireManageOrg(user, existingTemplate.getDeptId());
            if (!Objects.equals(item.getTemplateId(), request.getTemplateId())) {
                throw new BizException(422, "验收标准保存后不能更换交付物模板");
            }
        } else if (!"ENABLED".equals(template.getStatus())) {
            throw new BizException(409, "停用的交付物模板不能新增验收标准");
        }
        if (id == null) {
            item.setVersionNo("v1");
            item.setStatus("ENABLED");
            item.setCreatedBy(user.userId());
            item.setDeleted(0);
        } else {
            item.setVersionNo(nextVersion(item.getVersionNo()));
        }
        item.setTemplateId(request.getTemplateId());
        item.setStandardText(request.getStandardText().trim());
        item.setRequireReviewPassed(Boolean.TRUE.equals(request.getRequireReviewPassed()));
        item.setEvidenceRequirement(trimToNull(request.getEvidenceRequirement()));
        item.setUpdatedBy(user.userId());
        if (id == null) {
            acceptanceStandardMapper.insert(item);
        } else {
            acceptanceStandardMapper.updateById(item);
        }
        return toAcceptanceStandard(item, template);
    }

    @Transactional
    public ActionResultVO toggleAcceptanceStandard(AuthUser user, Long id, boolean enabled) {
        roleGuard.requireDepartmentModule(user);
        BizAcceptanceStandard item = requireAcceptanceStandard(id);
        BizDeliverableTemplate template = requireTemplate(item.getTemplateId());
        requireManageOrg(user, template.getDeptId());
        item.setStatus(enabled ? "ENABLED" : "DISABLED");
        item.setUpdatedBy(user.userId());
        acceptanceStandardMapper.updateById(item);
        return actionResult(String.valueOf(id), item.getStatus(), "验收标准状态已更新。", "ACCEPTANCE_STANDARD_TOGGLE");
    }

    public List<ScoreRuleVO> scoreRules(AuthUser user, Long orgId, String status) {
        roleGuard.requireDepartmentModule(user);
        return scoreRuleMapper.selectList(new LambdaQueryWrapper<BizScoreRule>()
                        .eq(BizScoreRule::getDeleted, 0)
                        .eq(orgId != null, BizScoreRule::getDeptId, orgId)
                        .eq(StringUtils.hasText(status), BizScoreRule::getStatus, status)
                        .orderByDesc(BizScoreRule::getUpdatedAt)
                        .orderByDesc(BizScoreRule::getId))
                .stream()
                .filter(item -> canManageOrg(user, item.getDeptId()))
                .map(this::toScoreRule)
                .toList();
    }

    @Transactional
    public ScoreRuleVO saveScoreRule(AuthUser user, Long id, ScoreRuleSaveReqDTO request) {
        roleGuard.requireDepartmentModule(user);
        requireManageOrg(user, request.getOrgId());
        if (request.getEffectiveStart() != null && request.getEffectiveEnd() != null
                && request.getEffectiveEnd().isBefore(request.getEffectiveStart())) {
            throw new BizException("规则失效日期不能早于生效日期");
        }
        BizScoreRule item = id == null ? new BizScoreRule() : requireScoreRule(id);
        if (id != null) {
            requireManageOrg(user, item.getDeptId());
        }
        if (id == null) {
            item.setStatus("DRAFT");
            item.setCreatedBy(user.userId());
            item.setDeleted(0);
        }
        item.setDeptId(request.getOrgId());
        item.setRuleName(request.getRuleName().trim());
        item.setEffectiveStart(request.getEffectiveStart());
        item.setEffectiveEnd(request.getEffectiveEnd());
        Map<String, Object> ruleJson = request.getRuleJson() == null ? defaultScoreRuleJson() : request.getRuleJson();
        validateScoreRuleJson(ruleJson);
        item.setRuleJson(jsonCodec.write(ruleJson));
        item.setUpdatedBy(user.userId());
        if (id == null) {
            scoreRuleMapper.insert(item);
        } else {
            scoreRuleMapper.updateById(item);
        }
        return toScoreRule(item);
    }

    @Transactional
    public ActionResultVO enableScoreRule(AuthUser user, Long id) {
        roleGuard.requireDepartmentModule(user);
        BizScoreRule target = requireScoreRule(id);
        requireManageOrg(user, target.getDeptId());
        List<BizScoreRule> enabled = scoreRuleMapper.selectList(new LambdaQueryWrapper<BizScoreRule>()
                .eq(BizScoreRule::getDeleted, 0)
                .eq(BizScoreRule::getDeptId, target.getDeptId())
                .eq(BizScoreRule::getStatus, "ENABLED"));
        for (BizScoreRule item : enabled) {
            if (item.getId().equals(target.getId())) {
                continue;
            }
            LocalDate targetStart = target.getEffectiveStart();
            if (targetStart != null && item.getEffectiveEnd() != null
                    && item.getEffectiveEnd().isBefore(targetStart)) {
                continue;
            }
            if (targetStart != null && (item.getEffectiveStart() == null
                    || item.getEffectiveStart().isBefore(targetStart))) {
                item.setEffectiveEnd(targetStart.minusDays(1));
            } else {
                item.setStatus("DISABLED");
            }
            item.setUpdatedBy(user.userId());
            scoreRuleMapper.updateById(item);
        }
        target.setStatus("ENABLED");
        target.setUpdatedBy(user.userId());
        scoreRuleMapper.updateById(target);
        return actionResult(String.valueOf(id), "ENABLED", "参考分规则已启用。", "SCORE_RULE_ENABLE");
    }

    public ScoreSimulationVO simulateScoreRule(AuthUser user, Long id, ScoreRuleSimulateReqDTO request) {
        roleGuard.requireDepartmentModule(user);
        BizScoreRule rule = requireScoreRule(id);
        requireManageOrg(user, rule.getDeptId());
        BigDecimal completionRatio = request.getCompletionRatio() == null ? BigDecimal.ZERO : request.getCompletionRatio();
        if (completionRatio.compareTo(BigDecimal.ZERO) < 0 || completionRatio.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(422, "完成比例必须在 0 到 100 之间");
        }
        int overdueCount = request.getOverdueCount() == null ? 0 : request.getOverdueCount();
        int rejectCount = request.getRejectCount() == null ? 0 : request.getRejectCount();
        if (overdueCount < 0 || rejectCount < 0) {
            throw new BizException(422, "逾期次数和驳回次数不能为负数");
        }
        Map<String, Object> ruleJson = jsonCodec.objectMap(rule.getRuleJson());
        validateScoreRuleJson(ruleJson);
        List<String> appliedFactors = new ArrayList<>();
        BigDecimal score = hasFactorRules(ruleJson)
                ? simulateFactorRules(ruleJson, completionRatio, overdueCount, rejectCount,
                        Boolean.TRUE.equals(request.getEvidenceComplete()), Boolean.TRUE.equals(request.getReviewPassed()),
                        appliedFactors)
                : simulateLegacyRule(ruleJson, completionRatio, overdueCount, rejectCount,
                        Boolean.TRUE.equals(request.getEvidenceComplete()), Boolean.TRUE.equals(request.getReviewPassed()),
                        appliedFactors);
        score = score
                .max(BigDecimal.ZERO)
                .min(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
        return new ScoreSimulationVO(StringUtils.hasText(request.getEmployeeName()) ? request.getEmployeeName() : "试算对象",
                score, appliedFactors,
                "参考分仅用于解释依据，不替代最终绩效裁量。");
    }

    public List<LedgerItemVO> departmentLedgers(AuthUser user, Long orgId, String periodType,
                                                 LocalDate periodStart, LocalDate periodEnd, String employeeName) {
        roleGuard.requireDepartmentModule(user);
        return leaderPerformanceService.departmentLedgers(user, orgId, periodType, periodStart, periodEnd, employeeName);
    }

    public List<ExportTaskVO> exportTasks(AuthUser user, String format, String status) {
        roleGuard.requireDepartmentModule(user);
        return exportTaskMapper.selectList(new LambdaQueryWrapper<BizExportTask>()
                        .eq(BizExportTask::getDeleted, 0)
                        .eq(StringUtils.hasText(status), BizExportTask::getStatus, status)
                        .orderByDesc(BizExportTask::getRequestedAt)
                        .orderByDesc(BizExportTask::getId))
                .stream()
                .filter(item -> canSeeExportTask(user, item))
                .map(this::toExportTask)
                .filter(item -> !StringUtils.hasText(format) || item.formats().contains(normalizeFormat(format)))
                .toList();
    }

    public ExportTaskVO exportTaskDetail(AuthUser user, String id) {
        return toExportTask(requireExportTask(user, id));
    }

    public ExportTaskVO createExportTask(AuthUser user, ExportTaskCreateReqDTO request) {
        roleGuard.requireDepartmentModule(user);
        List<String> formats = normalizeFormats(request.getFormats());
        String dimensionType = normalizeDimensionType(request.getDimensionType());
        if ("PERSON_LEDGER".equals(dimensionType)) {
            dataScopeService.requireDepartmentOwner(user, parseLongId(request.getDimensionId(), "员工"));
        } else if ("RESULT_CONFIRM_LIST".equals(dimensionType)
                && StringUtils.hasText(request.getDimensionId())
                && request.getDimensionId().startsWith("RESULTS:")) {
            for (Long resultId : parseSelectedResultIds(request.getDimensionId())) {
                requireDepartmentResult(user, String.valueOf(resultId));
            }
        } else if (StringUtils.hasText(request.getDimensionId())) {
            dataScopeService.requireDepartmentOrg(user, parseLongId(request.getDimensionId(), "组织"));
        }
        String periodType = normalizePeriodType(request.getPeriodType());
        LocalDate[] period = normalizeExportPeriod(periodType, request.getPeriodStart(), request.getPeriodEnd());
        BizExportTask task = new BizExportTask();
        task.setId("EXP-DEPT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
        task.setDimensionType(dimensionType);
        task.setDimensionId(request.getDimensionId());
        task.setDimensionName(exportDimensionName(dimensionType));
        task.setPeriodType(periodType);
        task.setPeriodStart(period[0]);
        task.setPeriodEnd(period[1]);
        task.setFormats(jsonCodec.write(formats));
        task.setIncludeEvidence(Boolean.TRUE.equals(request.getIncludeEvidence()));
        task.setWatermark(StringUtils.hasText(request.getWatermark()) ? request.getWatermark() : "部门、导出人、导出时间、周期");
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
        exportTaskWorker.generate(task.getId());
        return toExportTask(task);
    }

    @Transactional
    public ActionResultVO checkExportTask(AuthUser user, String id) {
        BizExportTask task = requireExportTaskForUpdate(user, id);
        if (Set.of("PENDING", "PROCESSING").contains(task.getStatus())) {
            throw new BizException(409, "导出任务仍在生成，请稍后刷新");
        }
        requireNotExpired(task);
        if (!"SUCCESS".equals(task.getStatus()) || task.getFilePath() == null) {
            throw new BizException(409, "导出任务未成功，暂不可校验");
        }
        if (!exportFileService.verify(task)) {
            task.setIntegrityStatus("MISMATCH");
            task.setStatus("NEEDS_REVIEW");
            task.setErrorMessage("文件校验值与任务记录不一致");
            exportTaskMapper.updateById(task);
            return actionResult(id, "NEEDS_REVIEW", "文件完整性校验失败，请重试导出任务。", "EXPORT_TASK_CHECK_FAILED");
        }
        List<String> missingItems = jsonCodec.stringList(task.getMissingItems());
        if (!missingItems.isEmpty()) {
            task.setIntegrityStatus("INCOMPLETE");
            task.setErrorMessage("资料包存在 " + missingItems.size() + " 项缺失或校验失败的证据");
            exportTaskMapper.updateById(task);
            return actionResult(id, "INCOMPLETE", "导出文件校验值一致，但资料包存在缺失或异常证据。", "EXPORT_TASK_CHECK_INCOMPLETE");
        }
        task.setIntegrityStatus("VERIFIED");
        task.setErrorMessage(null);
        exportTaskMapper.updateById(task);
        return actionResult(id, "CHECKED", "文件完整性校验已完成。", "EXPORT_TASK_CHECK");
    }

    @Transactional
    public ActionResultVO retryExportTask(AuthUser user, String id) {
        BizExportTask task = requireExportTaskForUpdate(user, id);
        if (!Set.of("FAILED", "NEEDS_REVIEW", "EXPIRED").contains(task.getStatus())) {
            throw new BizException(409, "只有失败、待确认或已过期任务可以重试");
        }
        exportFileService.deleteTaskFiles(task);
        task.setStatus("PENDING");
        task.setIntegrityStatus("PENDING_CHECK");
        task.setErrorMessage(null);
        task.setFinishedAt(null);
        task.setFileName(null);
        task.setFilePath(null);
        task.setChecksum(null);
        task.setExpireAt(null);
        task.setSizeText("--");
        task.setMissingItems("[]");
        exportTaskMapper.updateById(task);
        generateExportAfterCommit(task.getId());
        return actionResult(id, "PENDING", "失败任务已重新进入导出队列。", "EXPORT_TASK_RETRY");
    }

    public ExportDownloadVO exportDownloadInfo(AuthUser user, String id) {
        BizExportTask task = requireExportTask(user, id);
        requireNotExpired(task);
        if (!"SUCCESS".equals(task.getStatus())) {
            throw new BizException(409, "导出任务未成功，暂不可下载");
        }
        return new ExportDownloadVO(task.getId(), task.getStatus(), task.getFileName(),
                "/api/department/export-tasks/" + task.getId() + "/download", task.getExpireAt(), task.getChecksum());
    }

    public Resource downloadExport(AuthUser user, String id) {
        BizExportTask task = requireExportTask(user, id);
        requireNotExpired(task);
        if (!"SUCCESS".equals(task.getStatus()) || !exportFileService.verify(task)) {
            throw new BizException(409, "导出文件尚未生成或完整性校验失败");
        }
        return exportFileService.resource(task);
    }

    public ExportTaskVO exportDepartmentLedger(AuthUser user, ExportTaskCreateReqDTO request) {
        return createExportTask(user, request);
    }

    private MonthPlanApprovalItemVO toMonthPlanApproval(BizMonthPlan plan, SysUser owner) {
        List<BizMonthPlanItem> items = monthPlanItems(plan.getId());
        List<String> missing = monthPlanMissingFields(items);
        LocalDate deadline = items.stream().map(BizMonthPlanItem::getDeadline).filter(value -> value != null)
                .min(Comparator.naturalOrder()).orElse(null);
        YearMonth month = YearMonth.parse(plan.getPlanMonth());
        return new MonthPlanApprovalItemVO(String.valueOf(plan.getId()), "MP-" + plan.getPlanMonth() + "-" + plan.getId(),
                plan.getOwnerUserId(), owner == null ? "" : owner.getEmployeeNo(), owner == null ? "未知员工" : owner.getRealName(),
                plan.getDeptId(), dataScopeService.departmentName(plan.getDeptId()), month.getYear(), month.getMonthValue(),
                plan.getContent(), join(items.stream().map(BizMonthPlanItem::getDeliverable).toList()),
                deadline,
                toApprovalStatus(plan.getStatus()), plan.getApprovalComment(), plan.getApproverId(),
                approverName(plan.getApproverId()), plan.getApproveAt(),
                missing.isEmpty() ? "NORMAL" : "REQUIRED_FIELD_MISSING",
                missing, plan.getSubmitAt(), plan.getVersionNo() == null ? 1 : plan.getVersionNo(),
                items.stream().map(this::toMonthPlanApprovalDetailItem).toList());
    }

    private MonthPlanApprovalDetailItemVO toMonthPlanApprovalDetailItem(BizMonthPlanItem item) {
        return new MonthPlanApprovalDetailItemVO(
                item.getId(), defaultText(item.getTaskName(), ""), defaultText(item.getTaskContent(), ""),
                defaultText(item.getDeliverable(), ""), item.getPerformanceWeight(), item.getDeadline(),
                defaultText(item.getStatus(), "DRAFT")
        );
    }

    private ResultConfirmItemVO toResultConfirm(BizResult result, SysUser owner, BizMonthPlan plan) {
        List<EvidenceFileVO> evidences = resultEvidenceMapper.selectList(new LambdaQueryWrapper<BizResultEvidence>()
                        .eq(BizResultEvidence::getDeleted, 0)
                        .eq(BizResultEvidence::getResultId, result.getId())
                        .orderByAsc(BizResultEvidence::getId))
                .stream()
                .map(file -> new EvidenceFileVO(file.getId(), file.getFileName(), file.getFileType(),
                        defaultText(file.getStatus(), "UPLOADED"), Boolean.TRUE.equals(file.getReviewPassed())))
                .toList();
        String evidenceStatus = evidences.isEmpty() ? "MISSING" : defaultText(result.getEvidenceStatus(), "COMPLETE");
        String confirmStatus = toConfirmStatus(result, evidenceStatus);
        return new ResultConfirmItemVO(String.valueOf(result.getId()), "RES-" + result.getId(), result.getOwnerUserId(),
                owner == null ? "" : owner.getEmployeeNo(), owner == null ? "未知员工" : owner.getRealName(),
                result.getDeptId(), dataScopeService.departmentName(result.getDeptId()), result.getPlanType(), result.getPlanId(),
                plan == null ? "PLAN-" + result.getPlanId() : "MP-" + plan.getPlanMonth() + "-" + plan.getId(),
                result.getTitle(), BigDecimal.valueOf(result.getCompletionRate() == null ? 0 : result.getCompletionRate()),
                defaultText(result.getAutoLevel(), autoLevel(result.getCompletionRate())), evidenceStatus,
                result.getLeaderSuggestion(), jsonCodec.stringList(result.getIssueCodes()), result.getIssueText(), confirmStatus, evidences);
    }

    private List<DepartmentSummaryVO> departmentSummaries(AuthUser user, Long orgId, DashboardPeriod period,
                                                          List<MonthPlanApprovalItemVO> plans, List<ResultConfirmItemVO> results) {
        Set<Long> ownerIds = dataScopeService.departmentOwnerIds(user, orgId);
        Map<Long, SysUser> users = dataScopeService.userMap();
        Map<Long, SysDept> departments = dataScopeService.departmentMap();
        Set<Long> deptIds = ownerIds.stream().map(users::get).filter(item -> item != null).map(SysUser::getDeptId).collect(java.util.stream.Collectors.toSet());
        List<DepartmentSummaryVO> summaries = new ArrayList<>();
        for (Long deptId : deptIds) {
            int monthPlanCount = (int) plans.stream().filter(item -> deptId.equals(item.orgId())).count();
            int approved = (int) plans.stream().filter(item -> deptId.equals(item.orgId()) && "APPROVED".equals(item.status())).count();
            int pending = (int) plans.stream().filter(item -> deptId.equals(item.orgId()) && "PENDING_APPROVAL".equals(item.status())).count();
            int confirmed = (int) results.stream().filter(item -> deptId.equals(item.orgId()) && "CONFIRMED".equals(item.confirmStatus())).count();
            int missing = (int) plans.stream().filter(item -> deptId.equals(item.orgId())).mapToLong(item -> item.missingFields().size()).sum();
            int overdue = Math.toIntExact(dayPlanMapper.selectCount(new LambdaQueryWrapper<BizDayPlan>()
                    .eq(BizDayPlan::getDeleted, 0)
                    .eq(BizDayPlan::getDeptId, deptId)
                    .ge(BizDayPlan::getPlanDate, period.startMonth().atDay(1))
                    .le(BizDayPlan::getPlanDate, period.endMonth().atEndOfMonth())
                    .eq(BizDayPlan::getStatus, "PENDING")
                    .lt(BizDayPlan::getApprovalDueAt, LocalDateTime.now())));
            BigDecimal closure = results.stream().filter(item -> deptId.equals(item.orgId())).count() == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(confirmed * 100.0 / results.stream().filter(item -> deptId.equals(item.orgId())).count())
                    .setScale(2, RoundingMode.HALF_UP);
            SysDept dept = departments.get(deptId);
            summaries.add(new DepartmentSummaryVO(deptId, dept == null ? String.valueOf(deptId) : dept.getName(), monthPlanCount,
                    approved, pending, confirmed, closure, missing, overdue,
                    overdue > 0 ? "存在逾期未审" : missing > 0 ? "存在字段缺失" : "正常"));
        }
        return summaries.stream().sorted(Comparator.comparing(DepartmentSummaryVO::orgId)).toList();
    }

    private List<MetricVO> departmentMetrics(AuthUser user, List<MonthPlanApprovalItemVO> plans, List<ResultConfirmItemVO> results) {
        int exportCount = exportTasks(user, null, null).size();
        return List.of(
                new MetricVO("pending_month_approval", "月计划待审", (int) plans.stream().filter(item -> "PENDING_APPROVAL".equals(item.status())).count(), "primary"),
                new MetricVO("pending_result_confirm", "成果待确认", (int) results.stream().filter(item -> Set.of("PENDING_CONFIRM", "BLOCKED").contains(item.confirmStatus())).count(), "success"),
                new MetricVO("overdue_unapproved", "逾期未审", (int) plans.stream().filter(item -> item.deadline() != null && item.deadline().isBefore(LocalDate.now()) && "PENDING_APPROVAL".equals(item.status())).count(), "danger"),
                new MetricVO("export_task_count", "导出任务", exportCount, "warning")
        );
    }

    private TodoItemVO toTodo(BizTodo item) {
        return new TodoItemVO(String.valueOf(item.getId()), item.getSceneCode(), item.getTitle(), item.getTriggerText(),
                item.getReceiverId(), item.getReceiverName(), item.getObjectType(), item.getObjectId(), item.getDueAt(),
                item.getRequirementText(), item.getImpactText(), item.getStatus(), item.getRemindCount(), item.getRouteHint());
    }

    private DepartmentDayPlanReviewVO toDepartmentDayPlanReview(BizDayPlan plan) {
        SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
        BizMonthPlanItem item = plan.getMonthPlanItemId() == null ? null : monthPlanItemMapper.selectById(plan.getMonthPlanItemId());
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(plan.getContent())) {
            missing.add("workContent");
        }
        if (item == null || !StringUtils.hasText(item.getDeliverable())) {
            missing.add("deliverable");
        }
        LocalDateTime dueAt = plan.getApprovalDueAt() != null ? plan.getApprovalDueAt()
                : plan.getSubmitAt() == null ? plan.getPlanDate().plusDays(1).atTime(18, 0) : plan.getSubmitAt().plusDays(1);
        SysUser leader = plan.getReviewedBy() == null ? null : dataScopeService.requireUser(plan.getReviewedBy());
        SysUser departmentReviewer = plan.getApproverId() == null ? null : dataScopeService.requireUser(plan.getApproverId());
        return new DepartmentDayPlanReviewVO(
                String.valueOf(plan.getId()), plan.getOwnerUserId(), owner.getEmployeeNo(), owner.getRealName(),
                plan.getDeptId(), dataScopeService.departmentName(plan.getDeptId()), plan.getPlanDate(), plan.getSubmitAt(),
                plan.getContent(), item == null ? "" : defaultText(item.getDeliverable(), ""),
                dueAt, dueAt != null && dueAt.isBefore(LocalDateTime.now()) && "PENDING".equals(plan.getStatus()),
                missing, defaultText(plan.getAiCheckResult(), "NORMAL"), defaultText(plan.getReviewStatus(), "PENDING_COMMENT"),
                defaultText(plan.getRiskLevel(), "LOW"), defaultText(plan.getApprovalComment(), ""),
                leader == null ? "" : leader.getRealName(), plan.getReviewedAt(), plan.getStatus(),
                defaultText(plan.getDepartmentReviewComment(), ""),
                departmentReviewer == null ? "" : departmentReviewer.getRealName(), plan.getApproveAt()
        );
    }

    private AppealProcessVO toAppealProcess(BizEmployeeAppeal appeal) {
        SysUser owner = dataScopeService.requireUser(appeal.getOwnerUserId());
        BizResult result = appeal.getRelatedResultId() == null ? null : resultMapper.selectById(appeal.getRelatedResultId());
        return new AppealProcessVO(
                appeal.getId(),
                appeal.getAppealNo(),
                appeal.getTitle(),
                appeal.getReason(),
                appeal.getStatus(),
                appeal.getOwnerUserId(),
                owner.getRealName(),
                dataScopeService.departmentName(appeal.getDeptId()),
                appeal.getRelatedResultId(),
                result == null ? "未关联成果" : result.getTitle(),
                result == null ? "UNKNOWN" : result.getStatus(),
                result == null || result.getCompletionRate() == null ? 0 : result.getCompletionRate(),
                appeal.getHandlerId(),
                defaultText(appeal.getHandleComment(), ""),
                appeal.getCreatedAt(),
                appeal.getHandledAt()
        );
    }

    private DeliverableTemplateVO toTemplate(BizDeliverableTemplate item) {
        return new DeliverableTemplateVO(item.getId(), item.getDeptId(), dataScopeService.departmentName(item.getDeptId()),
                item.getTemplateName(), item.getEvidenceType(), Boolean.TRUE.equals(item.getRequiredFlag()), item.getAppliesTo(),
                item.getDescription(), item.getVersionNo(), item.getStatus(), item.getReferenceCount());
    }

    private AcceptanceStandardVO toAcceptanceStandard(BizAcceptanceStandard item, BizDeliverableTemplate template) {
        return new AcceptanceStandardVO(item.getId(), item.getTemplateId(), template.getTemplateName(), item.getStandardText(),
                Boolean.TRUE.equals(item.getRequireReviewPassed()), item.getEvidenceRequirement(), item.getVersionNo(), item.getStatus());
    }

    private ScoreRuleVO toScoreRule(BizScoreRule item) {
        return new ScoreRuleVO(item.getId(), item.getDeptId(), dataScopeService.departmentName(item.getDeptId()), item.getRuleName(),
                item.getStatus(), item.getEffectiveStart(), item.getEffectiveEnd(), jsonCodec.objectMap(item.getRuleJson()));
    }

    private ExportTaskVO toExportTask(BizExportTask task) {
        return new ExportTaskVO(task.getId(), task.getDimensionType(), task.getDimensionName(), task.getPeriodType(),
                task.getPeriodStart(), task.getPeriodEnd(), jsonCodec.stringList(task.getFormats()),
                Boolean.TRUE.equals(task.getIncludeEvidence()), task.getWatermark(), task.getIntegrityStatus(),
                jsonCodec.stringList(task.getMissingItems()), task.getChecksum(), task.getStatus(), task.getSizeText(),
                task.getRequestedBy(), task.getRequestedByName(), task.getRequestedAt(), task.getFinishedAt(),
                task.getExpireAt(), task.getErrorMessage());
    }

    private void requireNotExpired(BizExportTask task) {
        if ("EXPIRED".equals(task.getStatus())
                || task.getExpireAt() != null && task.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(410, "导出文件已过期，请重新创建任务");
        }
    }

    private List<BizMonthPlanItem> monthPlanItems(Long planId) {
        return monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, planId)
                .orderByAsc(BizMonthPlanItem::getSortNo)
                .orderByAsc(BizMonthPlanItem::getId));
    }

    private List<String> monthPlanMissingFields(List<BizMonthPlanItem> items) {
        List<String> missing = new ArrayList<>();
        if (items.isEmpty() || items.stream().anyMatch(item -> !StringUtils.hasText(item.getDeliverable()))) {
            missing.add("deliverable");
        }
        if (items.isEmpty() || items.stream().anyMatch(item -> item.getDeadline() == null)) {
            missing.add("deadline");
        }
        return missing;
    }

    private Map<Long, BizMonthPlan> monthPlanMap(List<BizResult> results) {
        Map<Long, BizMonthPlan> map = new HashMap<>();
        List<Long> ids = results.stream().map(BizResult::getPlanId).filter(id -> id != null).distinct().toList();
        if (!ids.isEmpty()) {
            monthPlanMapper.selectBatchIds(ids).forEach(plan -> map.put(plan.getId(), plan));
        }
        return map;
    }

    private Map<Long, BizDeliverableTemplate> templateMap() {
        Map<Long, BizDeliverableTemplate> map = new HashMap<>();
        templateMapper.selectList(new LambdaQueryWrapper<BizDeliverableTemplate>().eq(BizDeliverableTemplate::getDeleted, 0))
                .forEach(item -> map.put(item.getId(), item));
        return map;
    }

    private void syncMonthPlanItems(BizMonthPlan plan, Long userId) {
        for (BizMonthPlanItem item : monthPlanItems(plan.getId())) {
            item.setStatus(plan.getStatus());
            item.setUpdatedBy(userId);
            monthPlanItemMapper.updateById(item);
        }
    }

    private void markEvidenceReviewed(Long resultId, Long userId) {
        List<BizResultEvidence> items = resultEvidenceMapper.selectList(new LambdaQueryWrapper<BizResultEvidence>()
                .eq(BizResultEvidence::getDeleted, 0)
                .eq(BizResultEvidence::getResultId, resultId));
        for (BizResultEvidence item : items) {
            item.setReviewPassed(true);
            item.setStatus("REVIEW_PASSED");
            resultEvidenceMapper.updateById(item);
        }
    }

    private void completeObjectTodos(String objectType, String objectId) {
        List<BizTodo> items = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId)
                .ne(BizTodo::getStatus, "DONE"));
        for (BizTodo item : items) {
            item.setStatus("DONE");
            todoMapper.updateById(item);
        }
    }

    private String approverName(Long approverId) {
        if (approverId == null) {
            return "";
        }
        try {
            return dataScopeService.requireUser(approverId).getRealName();
        } catch (BizException ignored) {
            return "用户#" + approverId;
        }
    }

    private Set<Long> directLeaderOwnerIds(AuthUser user, Long orgId) {
        Set<Long> ownerIds = new java.util.HashSet<>(dataScopeService.leaderOwnerIds(user, orgId));
        boolean superAdmin = user.roles() != null && user.roles().contains("SUPER_ADMIN");
        if (!superAdmin) {
            ownerIds.removeIf(ownerId -> !user.userId().equals(dataScopeService.directLeaderId(ownerId)));
        }
        ownerIds.remove(user.userId());
        return ownerIds;
    }

    private void requireDirectLeader(AuthUser user, BizMonthPlan plan) {
        Long directLeaderId = dataScopeService.directLeaderId(plan.getOwnerUserId());
        boolean superAdmin = user.roles() != null && user.roles().contains("SUPER_ADMIN");
        if (!superAdmin && !user.userId().equals(directLeaderId)) {
            throw new BizException(403, "只有该员工的直属领导可以审批月计划");
        }
        if (user.userId().equals(plan.getOwnerUserId())) {
            throw new BizException(403, "不能审批自己的月计划");
        }
    }

    private void createMonthPlanResultNotification(BizMonthPlan plan, AuthUser approver) {
        SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
        BizTodo todo = new BizTodo();
        todo.setSceneCode("MONTH_PLAN_APPROVAL_RESULT");
        todo.setTitle("APPROVED".equals(plan.getStatus()) ? "月计划审批通过" : "月计划审批驳回");
        todo.setTriggerText(approver.realName() + "已处理" + plan.getTitle());
        todo.setReceiverId(owner.getId());
        todo.setReceiverName(owner.getRealName());
        todo.setObjectType("MONTH_PLAN_RESULT");
        todo.setObjectId(String.valueOf(plan.getId()));
        todo.setRequirementText("REJECTED".equals(plan.getStatus()) ? "查看驳回原因并修改后重新提交" : "按审批后的月计划执行");
        todo.setImpactText("审批结果已同步到部门负责人只读视图");
        todo.setMessageType("NOTICE");
        todo.setStatus("UNREAD");
        todo.setRemindCount(0);
        todo.setRouteHint("/employee/month-plans/" + plan.getId());
        todo.setDeptId(plan.getDeptId());
        todo.setCreatedBy(approver.userId());
        todo.setUpdatedBy(approver.userId());
        todo.setCreatedAt(LocalDateTime.now());
        todo.setUpdatedAt(LocalDateTime.now());
        todo.setDeleted(0);
        todoMapper.insert(todo);
    }

    private void notifyResultOutcome(AuthUser operator, BizResult result, boolean confirmed) {
        String outcome = confirmed ? "最终确认" : "驳回";
        messageService.createNotice(result.getOwnerUserId(), "RESULT_FINAL_RESULT", "成果已" + outcome,
                operator.realName() + "已" + outcome + "成果“" + result.getTitle() + "”，请查看处理意见。",
                "RESULT_FINAL", String.valueOf(result.getId()), "/employee/results",
                result.getDeptId(), operator.userId());
        if (result.getSuggestedBy() != null && !result.getSuggestedBy().equals(result.getOwnerUserId())) {
            messageService.createNotice(result.getSuggestedBy(), "RESULT_FINAL_RESULT", "成果最终处理完成",
                    operator.realName() + "已" + outcome + "你提交建议的成果“" + result.getTitle() + "”。",
                    "RESULT_FINAL", String.valueOf(result.getId()), "/leader/result-suggest",
                    result.getDeptId(), operator.userId());
        }
    }

    private void notifyDayPlanDepartmentOutcome(AuthUser operator, BizDayPlan plan, boolean approved) {
        String outcome = approved ? "补审通过" : "退回补充";
        String employeeRoute = "/employee/day-plans?date=" + plan.getPlanDate();
        messageService.createNotice(plan.getOwnerUserId(), "DAY_PLAN_DEPARTMENT_RESULT", "日计划" + outcome,
                operator.realName() + "已完成" + plan.getPlanDate() + "日计划的部门补审，请查看处理意见。",
                "DAY_PLAN_RESULT", String.valueOf(plan.getId()), employeeRoute,
                plan.getDeptId(), operator.userId());
        Long leaderId = dataScopeService.directLeaderId(plan.getOwnerUserId());
        if (leaderId != null && !leaderId.equals(plan.getOwnerUserId())) {
            messageService.createNotice(leaderId, "DAY_PLAN_DEPARTMENT_RESULT", "日计划风险补审完成",
                    operator.realName() + "已将你标记风险的" + plan.getPlanDate() + "日计划处理为“" + outcome + "”。",
                    "DAY_PLAN_RESULT", String.valueOf(plan.getId()), "/leader/daily-review",
                    plan.getDeptId(), operator.userId());
        }
    }

    private BizMonthPlan requireDepartmentMonthPlan(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizMonthPlan plan = requireMonthPlan(id);
        dataScopeService.requireDepartmentOwner(user, plan.getOwnerUserId());
        return plan;
    }

    private BizMonthPlan requireLeaderMonthPlanForUpdate(AuthUser user, String id) {
        roleGuard.requireLeaderModule(user);
        BizMonthPlan plan = monthPlanMapper.selectForUpdateById(parseLongId(id, "月计划"));
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "月计划审批记录不存在");
        }
        requireDirectLeader(user, plan);
        return plan;
    }

    private void requirePendingMonthPlanApproval(BizMonthPlan plan) {
        if (!"PENDING".equals(plan.getStatus())) {
            throw new BizException(409, "月计划已完成审批，不能重复处理");
        }
    }

    private BizDayPlan requireDepartmentDayPlan(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizDayPlan plan = dayPlanMapper.selectById(parseLongId(id, "日计划"));
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "日计划补审记录不存在");
        }
        dataScopeService.requireDepartmentOwner(user, plan.getOwnerUserId());
        return plan;
    }

    private BizDayPlan requireDepartmentDayPlanForUpdate(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizDayPlan plan = dayPlanMapper.selectForUpdateById(parseLongId(id, "日计划"));
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "日计划补审记录不存在");
        }
        dataScopeService.requireDepartmentOwner(user, plan.getOwnerUserId());
        return plan;
    }

    private void requirePendingDayPlanReview(BizDayPlan plan) {
        if (!"PENDING".equals(plan.getStatus()) || !"RISK_MARKED".equals(plan.getReviewStatus())) {
            throw new BizException(409, "日计划已完成补审，不能重复处理");
        }
    }

    private BizResult requireDepartmentResult(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizResult result = requireResult(id);
        dataScopeService.requireDepartmentOwner(user, result.getOwnerUserId());
        return result;
    }

    private BizResult requireDepartmentResultForUpdate(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizResult result = resultMapper.selectForUpdateById(parseLongId(id, "成果"));
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "成果确认记录不存在");
        }
        dataScopeService.requireDepartmentOwner(user, result.getOwnerUserId());
        return result;
    }

    private BizMonthPlan requireMonthPlan(String id) {
        BizMonthPlan plan = monthPlanMapper.selectById(parseLongId(id, "月计划"));
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "月计划审批记录不存在");
        }
        return plan;
    }

    private BizResult requireResult(String id) {
        BizResult result = resultMapper.selectById(parseLongId(id, "成果"));
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "成果确认记录不存在");
        }
        return result;
    }

    private BizTodo requireTodo(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizTodo todo = todoMapper.selectById(parseLongId(id, "待办"));
        if (todo == null || Integer.valueOf(1).equals(todo.getDeleted())) {
            throw new BizException(404, "待办不存在");
        }
        if (!isSuperAdmin(user) && !user.userId().equals(todo.getReceiverId())) {
            throw new BizException(403, "无权处理该待办");
        }
        return todo;
    }

    private BizTodo requireTodoForUpdate(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizTodo todo = todoMapper.selectForUpdateById(parseLongId(id, "待办"));
        if (todo == null || Integer.valueOf(1).equals(todo.getDeleted())) {
            throw new BizException(404, "待办不存在");
        }
        if (!isSuperAdmin(user) && !user.userId().equals(todo.getReceiverId())) {
            throw new BizException(403, "无权处理该待办");
        }
        return todo;
    }

    private void requireOpenTodo(BizTodo todo) {
        if ("DONE".equals(todo.getStatus())) {
            throw new BizException(409, "已完成待办不能再次催办或升级");
        }
    }

    private BizEmployeeAppeal requireDepartmentAppeal(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizEmployeeAppeal appeal = appealMapper.selectById(parseLongId(id, "申诉"));
        if (appeal == null || Integer.valueOf(1).equals(appeal.getDeleted())) {
            throw new BizException(404, "申诉记录不存在");
        }
        dataScopeService.requireDepartmentOwner(user, appeal.getOwnerUserId());
        if (!isSuperAdmin(user) && appeal.getHandlerId() != null && !user.userId().equals(appeal.getHandlerId())) {
            throw new BizException(403, "该申诉已分配给其他处理人");
        }
        return appeal;
    }

    private BizEmployeeAppeal requireDepartmentAppealForUpdate(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizEmployeeAppeal appeal = appealMapper.selectForUpdateById(parseLongId(id, "申诉"));
        if (appeal == null || Integer.valueOf(1).equals(appeal.getDeleted())) {
            throw new BizException(404, "申诉记录不存在");
        }
        dataScopeService.requireDepartmentOwner(user, appeal.getOwnerUserId());
        if (!isSuperAdmin(user) && appeal.getHandlerId() != null && !user.userId().equals(appeal.getHandlerId())) {
            throw new BizException(403, "该申诉已分配给其他处理人");
        }
        return appeal;
    }

    private BizDeliverableTemplate requireTemplate(Long id) {
        BizDeliverableTemplate item = templateMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "交付物模板不存在");
        }
        return item;
    }

    private BizAcceptanceStandard requireAcceptanceStandard(Long id) {
        BizAcceptanceStandard item = acceptanceStandardMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "验收标准不存在");
        }
        return item;
    }

    private BizScoreRule requireScoreRule(Long id) {
        BizScoreRule item = scoreRuleMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "参考分规则不存在");
        }
        return item;
    }

    private BizExportTask requireExportTask(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizExportTask item = exportTaskMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "导出任务不存在");
        }
        if (!canSeeExportTask(user, item)) {
            throw new BizException(403, "无权查看该导出任务");
        }
        return item;
    }

    private BizExportTask requireExportTaskForUpdate(AuthUser user, String id) {
        roleGuard.requireDepartmentModule(user);
        BizExportTask item = exportTaskMapper.selectForUpdateById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "导出任务不存在");
        }
        if (!canSeeExportTask(user, item)) {
            throw new BizException(403, "无权查看该导出任务");
        }
        return item;
    }

    private void generateExportAfterCommit(String taskId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            exportTaskWorker.generate(taskId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                exportTaskWorker.generate(taskId);
            }
        });
    }

    private boolean canSeeExportTask(AuthUser user, BizExportTask item) {
        return isSuperAdmin(user) || user.userId().equals(item.getRequestedBy()) || canManageOrg(user, item.getDeptId());
    }

    private void requireManageOrg(AuthUser user, Long orgId) {
        if (!canManageOrg(user, orgId)) {
            throw new BizException(403, "所选组织不在当前账号的数据范围内");
        }
    }

    private String normalizeTemplateEvidenceType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!TEMPLATE_EVIDENCE_TYPES.contains(normalized)) {
            throw new BizException(422, "不支持的证据类型");
        }
        return normalized;
    }

    private String normalizeTemplateAppliesTo(String value) {
        Set<String> scenes = new LinkedHashSet<>();
        for (String raw : value.split(",")) {
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if (!TEMPLATE_SCENES.contains(normalized)) {
                throw new BizException(422, "不支持的适用场景：" + raw.trim());
            }
            scenes.add(normalized);
        }
        if (scenes.isEmpty()) {
            throw new BizException(422, "适用场景不能为空");
        }
        return String.join(",", scenes);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean canManageOrg(AuthUser user, Long orgId) {
        return isSuperAdmin(user) || user.deptId() != null && dataScopeService.departmentScope(user.deptId()).contains(orgId);
    }

    private boolean isSuperAdmin(AuthUser user) {
        return user.roles() != null && user.roles().contains("SUPER_ADMIN");
    }

    private Long parseLongId(String id, String objectName) {
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

    private String toApprovalStatus(String status) {
        return switch (status) {
            case "PENDING" -> "PENDING_APPROVAL";
            case "REJECTED" -> "REJECTED";
            case "APPROVED" -> "APPROVED";
            default -> status;
        };
    }

    private String toConfirmStatus(BizResult result, String evidenceStatus) {
        if ("CONFIRMED".equals(result.getStatus()) || "REJECTED".equals(result.getStatus())) {
            return result.getStatus();
        }
        if (!"COMPLETE".equals(evidenceStatus) || !"SUGGEST_CONFIRM".equals(result.getSuggestionStatus())) {
            return "BLOCKED";
        }
        return "PENDING_CONFIRM";
    }

    private String autoLevel(Integer completionRate) {
        int rate = completionRate == null ? 0 : completionRate;
        return rate >= 100 ? "DONE" : rate >= 80 ? "BASIC_DONE" : "PARTIAL_DONE";
    }

    private String join(List<String> values) {
        return values.stream().filter(StringUtils::hasText).distinct().reduce((left, right) -> left + "；" + right).orElse("");
    }

    private List<String> normalizeFormats(List<String> formats) {
        if (formats == null || formats.isEmpty()) {
            throw new BizException("请至少选择一种导出格式");
        }
        return formats.stream().map(this::normalizeFormat).distinct().toList();
    }

    private String approvalStorageStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "PENDING_APPROVAL" -> "PENDING";
            case "APPROVED" -> "APPROVED";
            case "REJECTED" -> "REJECTED";
            case "RETURNED" -> "__NO_MATCH__";
            default -> throw new BizException(422, "不支持的月计划审批状态");
        };
    }

    private List<Long> parseSelectedResultIds(String dimensionId) {
        String rawIds = dimensionId.substring("RESULTS:".length());
        if (!StringUtils.hasText(rawIds)) {
            throw new BizException(400, "请选择需要导出的成果");
        }
        try {
            return List.of(rawIds.split(",")).stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::valueOf)
                    .distinct()
                    .toList();
        } catch (NumberFormatException ex) {
            throw new BizException(400, "成果编号格式错误");
        }
    }

    private String normalizeDimensionType(String dimensionType) {
        String normalized = StringUtils.hasText(dimensionType)
                ? dimensionType.trim().toUpperCase(Locale.ROOT)
                : "DEPARTMENT_LEDGER";
        if (!EXPORT_DIMENSIONS.contains(normalized)) {
            throw new BizException("不支持的导出维度");
        }
        return normalized;
    }

    private String normalizePeriodType(String periodType) {
        String normalized = StringUtils.hasText(periodType)
                ? periodType.trim().toUpperCase(Locale.ROOT)
                : "MONTH";
        if (!EXPORT_PERIOD_TYPES.contains(normalized)) {
            throw new BizException("导出周期仅支持日、月、季度、年度");
        }
        return normalized;
    }

    private LocalDate[] normalizeExportPeriod(String periodType, LocalDate start, LocalDate end) {
        if ((start == null) != (end == null)) {
            throw new BizException("导出开始日期和结束日期必须同时填写");
        }
        if (start != null) {
            if (start.isAfter(end)) {
                throw new BizException("导出开始日期不能晚于结束日期");
            }
            return new LocalDate[]{start, end};
        }
        LocalDate today = LocalDate.now();
        return switch (periodType) {
            case "DAY" -> new LocalDate[]{today, today};
            case "QUARTER" -> {
                int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate quarterStart = LocalDate.of(today.getYear(), firstMonth, 1);
                yield new LocalDate[]{quarterStart, quarterStart.plusMonths(3).minusDays(1)};
            }
            case "YEAR" -> new LocalDate[]{LocalDate.of(today.getYear(), 1, 1), LocalDate.of(today.getYear(), 12, 31)};
            default -> {
                LocalDate monthStart = today.withDayOfMonth(1);
                yield new LocalDate[]{monthStart, monthStart.plusMonths(1).minusDays(1)};
            }
        };
    }

    private String normalizeFormat(String format) {
        if (!StringUtils.hasText(format)) {
            throw new BizException("导出格式不能为空");
        }
        String normalized = format.trim().toUpperCase(Locale.ROOT);
        if (Set.of("DOC", "DOCX").contains(normalized)) {
            normalized = "WORD";
        }
        if (!EXPORT_FORMATS.contains(normalized)) {
            throw new BizException("导出格式仅支持 PDF、Word、Zip");
        }
        return normalized;
    }

    private String exportDimensionName(String dimensionType) {
        return switch (dimensionType) {
            case "MONTH_PLAN_APPROVAL_LIST" -> "月计划查看清单";
            case "RESULT_CONFIRM_LIST" -> "成果确认证据清单";
            case "PERSON_LEDGER" -> "个人绩效资料包";
            case "APPEAL_PACKAGE" -> "申诉资料包";
            case "QUARTER_SUMMARY" -> "季度汇总资料包";
            default -> "部门台账导出";
        };
    }

    private void validateScoreRuleJson(Map<String, Object> ruleJson) {
        Object rawFactors = ruleJson.get("factors");
        if (rawFactors == null) {
            return;
        }
        if (!(rawFactors instanceof List<?> factors) || factors.isEmpty()) {
            throw new BizException(422, "参考分规则至少需要一个计算因素");
        }
        Set<String> codes = new java.util.HashSet<>();
        BigDecimal enabledWeight = BigDecimal.ZERO;
        for (Object rawFactor : factors) {
            if (!(rawFactor instanceof Map<?, ?> factor)) {
                throw new BizException(422, "参考分因素格式错误");
            }
            String code = String.valueOf(factor.get("code"));
            if (!SCORE_FACTOR_CODES.contains(code)) {
                throw new BizException(422, "不支持的参考分因素：" + code);
            }
            if (!codes.add(code)) {
                throw new BizException(422, "参考分因素不能重复：" + code);
            }
            BigDecimal weight = decimalValue(factor.get("weight"), BigDecimal.ZERO);
            if (weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(new BigDecimal("100")) > 0) {
                throw new BizException(422, "参考分因素权重必须在 0 到 100 之间");
            }
            if (!Boolean.FALSE.equals(factor.get("enabled"))) {
                enabledWeight = enabledWeight.add(weight);
            }
        }
        if (enabledWeight.compareTo(BigDecimal.ZERO) <= 0 || enabledWeight.compareTo(new BigDecimal("100")) > 0) {
            throw new BizException(422, "启用因素权重合计必须大于 0 且不超过 100");
        }
    }

    private boolean hasFactorRules(Map<String, Object> ruleJson) {
        return ruleJson.get("factors") instanceof List<?> factors && !factors.isEmpty();
    }

    private BigDecimal simulateFactorRules(Map<String, Object> ruleJson, BigDecimal completionRatio,
                                           int overdueCount, int rejectCount, boolean evidenceComplete,
                                           boolean reviewPassed, List<String> appliedFactors) {
        BigDecimal score = BigDecimal.ZERO;
        List<?> factors = (List<?>) ruleJson.get("factors");
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
            appliedFactors.add(code);
        }
        return score;
    }

    private BigDecimal simulateLegacyRule(Map<String, Object> ruleJson, BigDecimal completionRatio,
                                          int overdueCount, int rejectCount, boolean evidenceComplete,
                                          boolean reviewPassed, List<String> appliedFactors) {
        appliedFactors.addAll(List.of("completion_ratio", "overdue_count", "reject_count",
                "evidence_complete", "review_passed"));
        return decimalValue(ruleJson.get("baseScore"), new BigDecimal("60"))
                .add(completionRatio.multiply(decimalValue(ruleJson.get("completionWeight"), new BigDecimal("0.20"))))
                .add(evidenceComplete ? decimalValue(ruleJson.get("evidenceBonus"), new BigDecimal("10")) : BigDecimal.ZERO)
                .add(reviewPassed ? decimalValue(ruleJson.get("reviewBonus"), new BigDecimal("5")) : BigDecimal.ZERO)
                .subtract(decimalValue(ruleJson.get("overduePenalty"), new BigDecimal("2"))
                        .multiply(BigDecimal.valueOf(overdueCount)))
                .subtract(decimalValue(ruleJson.get("rejectPenalty"), new BigDecimal("3"))
                        .multiply(BigDecimal.valueOf(rejectCount)));
    }

    private BigDecimal decimalValue(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new BizException(422, "参考分规则存在无效数值");
        }
    }

    private Map<String, Object> defaultScoreRuleJson() {
        return Map.of("factors", List.of(
                Map.of("code", "completion_ratio", "name", "完成比例", "weight", 70, "enabled", true),
                Map.of("code", "overdue_count", "name", "逾期提交", "weight", 10, "penaltyPerTime", 2, "enabled", true),
                Map.of("code", "reject_count", "name", "驳回次数", "weight", 10, "penaltyPerTime", 3, "enabled", true),
                Map.of("code", "review_passed", "name", "评审通过", "weight", 10, "enabled", true)
        ));
    }

    private String nextVersion(String versionNo) {
        if (StringUtils.hasText(versionNo) && versionNo.matches("v\\d+")) {
            return "v" + (Integer.parseInt(versionNo.substring(1)) + 1);
        }
        return "v2";
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

    private String optionalActionComment(String comment, String fallback, int maxLength) {
        return StringUtils.hasText(comment)
                ? requireActionComment(comment, "处理说明不能为空", maxLength)
                : fallback;
    }

    private ActionResultVO actionResult(String objectId, String status, String message, String auditActionCode) {
        return new ActionResultVO(objectId, status, message, auditActionCode, true);
    }

    private record DashboardPeriod(YearMonth startMonth, YearMonth endMonth) {
    }
}
