package com.planning.platform.employee.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.planning.platform.ai.model.AiReviewModels.ReviewVO;
import com.planning.platform.ai.service.AiReviewService;
import com.planning.platform.ai.service.EvidenceDocumentService;
import com.planning.platform.ai.service.EvidenceDocumentService.EvidenceDocument;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.employee.controller.EmployeeController;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.domain.BizEmployeeAppeal;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.planning.service.PlanningAccessService;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.domain.BizPlanAdjustment;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.performance.service.ExportFileService;
import com.planning.platform.performance.service.PerformanceJsonCodec;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.system.domain.SysAuditLog;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import com.planning.platform.system.service.AuditLogService;
import com.planning.platform.system.service.WorkdayCalendarService;
import com.planning.platform.system.service.WorkdayCalendarService.CalendarDay;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private static final String DRAFT = "DRAFT";
    private static final String PENDING = "PENDING";
    private static final String APPROVED = "APPROVED";
    private static final String REJECTED = "REJECTED";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String REGULAR = "REGULAR";
    private static final String EXTRA = "EXTRA";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BizMonthPlanMapper monthPlanMapper;
    private final BizMonthPlanItemMapper monthPlanItemMapper;
    private final BizDayPlanMapper dayPlanMapper;
    private final BizResultMapper resultMapper;
    private final BizResultEvidenceMapper resultEvidenceMapper;
    private final BizEmployeeAppealMapper appealMapper;
    private final SysAuditLogMapper auditLogMapper;
    private final AuditLogService auditLogService;
    private final PlanningAccessService accessService;
    private final PerformanceDataScopeService dataScopeService;
    private final BizTodoMapper todoMapper;
    private final BizPlanAdjustmentMapper planAdjustmentMapper;
    private final BizExportTaskMapper exportTaskMapper;
    private final ExportFileService exportFileService;
    private final PerformanceJsonCodec jsonCodec;
    private final WorkdayCalendarService workdayCalendarService;
    private final UserMessageService messageService;
    private final AiReviewService aiReviewService;
    private final EvidenceDocumentService evidenceDocumentService;

    @Value("${planning.storage.upload-root:uploads/employee-results}")
    private String uploadRootPath;
    private final ObjectMapper objectMapper;

    public Map<String, Object> dashboard(AuthUser user, String month) {
        month = normalizePlanMonth(month);
        List<BizMonthPlan> monthPlans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, user.userId())
                .eq(StringUtils.hasText(month), BizMonthPlan::getPlanMonth, month)
                .orderByDesc(BizMonthPlan::getPlanMonth)
                .orderByDesc(BizMonthPlan::getId));
        LocalDate monthStart = LocalDate.parse(month + "-01");
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);
        List<BizDayPlan> dayPlans = dayPlanMapper.selectList(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getOwnerUserId, user.userId())
                .ge(BizDayPlan::getPlanDate, monthStart)
                .le(BizDayPlan::getPlanDate, monthEnd)
                .orderByDesc(BizDayPlan::getPlanDate)
                .orderByDesc(BizDayPlan::getId));
        List<CalendarDay> workdayCalendar = workdayCalendarService.range(monthStart, monthEnd);
        Set<LocalDate> dayPlanDates = dayPlans.stream().map(BizDayPlan::getPlanDate).collect(java.util.stream.Collectors.toSet());
        LocalDate today = LocalDate.now();
        long missingRequiredDayPlanCount = workdayCalendar.stream()
                .filter(CalendarDay::forceReport)
                .filter(item -> !item.date().isAfter(today))
                .filter(item -> !dayPlanDates.contains(item.date()))
                .count();
        List<Long> monthPlanIds = monthPlans.stream().map(BizMonthPlan::getId).toList();
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, user.userId())
                .orderByDesc(BizResult::getResultDate)
                .orderByDesc(BizResult::getId))
                .stream()
                .filter(result -> monthPlanIds.contains(result.getPlanId()))
                .toList();
        long openAppealCount = appealMapper.selectCount(new LambdaQueryWrapper<BizEmployeeAppeal>()
                .eq(BizEmployeeAppeal::getDeleted, 0)
                .eq(BizEmployeeAppeal::getOwnerUserId, user.userId())
                .in(BizEmployeeAppeal::getStatus, List.of("SUBMITTED", "PROCESSING")));

        Map<String, Object> data = new HashMap<>();
        data.put("currentMonth", month);
        data.put("orgName", dataScopeService.departmentName(user.deptId()));
        data.put("monthPlans", monthPlans.stream().map(this::toMonthPlanCard).toList());
        data.put("dayPlanCalendar", dayPlans.stream().map(this::toDayPlanCalendarItem).toList());
        data.put("workdayCalendar", workdayCalendar.stream()
                .map(item -> toWorkdayCalendarItem(item, dayPlanDates, today)).toList());
        data.put("summary", Map.of(
                "monthPlanCount", monthPlans.size(),
                "submittedResultCount", (int) results.stream().filter(r -> PENDING.equals(r.getStatus()) || CONFIRMED.equals(r.getStatus())).count(),
                "averageCompletionRate", results.isEmpty() ? 0 : (int) results.stream().mapToInt(this::completionRateOf).average().orElse(0),
                "openAppealCount", openAppealCount,
                "missingRequiredDayPlanCount", missingRequiredDayPlanCount
        ));
        return data;
    }

    public Map<String, Object> monthPlanDetail(AuthUser user, Long id) {
        BizMonthPlan plan = requireMonthPlan(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        SysUser owner = dataScopeService.requireUser(plan.getOwnerUserId());
        Map<String, Object> data = new HashMap<>();
        data.put("id", plan.getId());
        data.put("planMonth", plan.getPlanMonth());
        data.put("employeeName", owner.getRealName());
        data.put("departmentName", dataScopeService.departmentName(plan.getDeptId()));
        data.put("status", toEmployeePlanStatus(plan.getStatus()));
        data.put("resultStatus", inferResultStatus(plan.getId(), plan.getOwnerUserId()));
        data.put("updatedAt", plan.getUpdatedAt());
        data.put("summary", plan.getContent());
        data.put("approvalComment", plan.getApprovalComment() == null ? "" : plan.getApprovalComment());
        data.put("approvedAt", plan.getApproveAt());
        data.put("items", monthPlanItems(plan));
        data.put("deliverables", deliverables(plan));
        data.put("resultSummary", Map.of(
                "submittedCount", countResults(plan.getOwnerUserId(), plan.getPlanMonth()),
                "confirmedCount", countConfirmedResults(plan.getOwnerUserId(), plan.getPlanMonth()),
                "rejectedCount", countRejectedResults(plan.getOwnerUserId(), plan.getPlanMonth()),
                "latestVersion", latestResultVersion(plan.getOwnerUserId(), plan.getPlanMonth()),
                "overallCompletionRate", completionRateOfLatest(plan.getOwnerUserId(), plan.getPlanMonth())
        ));
        data.put("confirmRecords", confirmRecords(plan.getId()));
        return data;
    }

    @Transactional
    public Map<String, Object> createMonthPlanDraft(AuthUser user, EmployeeController.SaveMonthPlanDraftReq request) {
        BizMonthPlan plan = new BizMonthPlan();
        String planMonth = normalizePlanMonth(StringUtils.hasText(request.getPlanMonth())
                ? request.getPlanMonth()
                : YearMonth.now(BUSINESS_ZONE).toString());
        requireWritablePlanMonth(planMonth);
        dataScopeService.lockUser(user.userId());
        requireUniqueMonthPlan(user.userId(), planMonth, null);
        plan.setTitle(buildMonthTitle(planMonth, request.getSummary()));
        plan.setPlanMonth(planMonth);
        plan.setContent(request.getSummary() == null ? "" : request.getSummary());
        plan.setOwnerUserId(user.userId());
        plan.setDeptId(user.deptId());
        plan.setStatus(DRAFT);
        plan.setVersionNo(1);
        plan.setCreatedBy(user.userId());
        plan.setUpdatedBy(user.userId());
        plan.setDeleted(0);
        monthPlanMapper.insert(plan);
        replaceMonthPlanItems(user, plan, request.getItems());
        auditLogService.success(user, "EMPLOYEE_MONTH_PLAN_CREATE", "MONTH_PLAN", plan.getId(),
                auditDetail("planMonth", plan.getPlanMonth(), "itemCount", itemCount(request.getItems())));
        return monthPlanDetail(user, plan.getId());
    }

    @Transactional
    public Map<String, Object> saveMonthPlanDraft(AuthUser user, Long id, EmployeeController.SaveMonthPlanDraftReq request) {
        BizMonthPlan current = requireMonthPlan(id);
        accessService.requireOwner(user, current.getOwnerUserId());
        dataScopeService.lockUser(current.getOwnerUserId());
        BizMonthPlan plan = requireMonthPlanForUpdate(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException("只有草稿或驳回状态可编辑");
        }
        if (StringUtils.hasText(request.getPlanMonth())) {
            String planMonth = normalizePlanMonth(request.getPlanMonth());
            requireWritablePlanMonth(planMonth);
            requireUniqueMonthPlan(plan.getOwnerUserId(), planMonth, plan.getId());
            plan.setPlanMonth(planMonth);
        }
        requireWritablePlanMonth(plan.getPlanMonth());
        plan.setTitle(buildMonthTitle(plan.getPlanMonth(), request.getSummary()));
        plan.setContent(request.getSummary() == null ? "" : request.getSummary());
        if (REJECTED.equals(plan.getStatus())) {
            plan.setApprovalComment(null);
            plan.setApproverId(null);
            plan.setApproveAt(null);
        }
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        replaceMonthPlanItems(user, plan, request.getItems());
        auditLogService.success(user, "EMPLOYEE_MONTH_PLAN_UPDATE", "MONTH_PLAN", plan.getId(),
                auditDetail("planMonth", plan.getPlanMonth(), "itemCount", itemCount(request.getItems())));
        return monthPlanDetail(user, id);
    }

    @Transactional
    public void deleteMonthPlanItem(AuthUser user, Long planId, Long itemId) {
        BizMonthPlan plan = requireMonthPlanForUpdate(planId);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException("只有草稿或驳回状态可编辑");
        }
        BizMonthPlanItem item = monthPlanItemMapper.selectOne(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getId, itemId)
                .eq(BizMonthPlanItem::getMonthPlanId, planId)
                .eq(BizMonthPlanItem::getDeleted, 0)
                .last("limit 1"));
        if (item == null) {
            throw new BizException(404, "月计划明细不存在");
        }
        monthPlanItemMapper.deleteById(itemId);
        auditLogService.success(user, "EMPLOYEE_MONTH_PLAN_ITEM_DELETE", "MONTH_PLAN_ITEM", itemId,
                auditDetail("monthPlanId", planId, "taskName", item.getTaskName()));
    }

    @Transactional
    public Map<String, Object> submitMonthPlan(AuthUser user, Long id) {
        BizMonthPlan plan = requireMonthPlanForUpdate(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
            throw new BizException("只有草稿或驳回状态可提交");
        }
        requireWritablePlanMonth(plan.getPlanMonth());
        validateMonthPlanForSubmit(plan);
        aiReviewService.ensurePlanReview(user, AiReviewService.MONTH_PLAN, plan.getId());
        Long directLeaderId = dataScopeService.directLeaderId(plan.getOwnerUserId());
        if (directLeaderId == null) {
            throw new BizException(422, "当前员工未配置直属领导，无法提交月计划审批");
        }
        if (directLeaderId.equals(plan.getOwnerUserId())) {
            throw new BizException(422, "直属领导不能与计划员工为同一人");
        }
        if (REJECTED.equals(plan.getStatus())) {
            plan.setVersionNo((plan.getVersionNo() == null ? 1 : plan.getVersionNo()) + 1);
        }
        plan.setStatus(PENDING);
        plan.setSubmitAt(LocalDateTime.now());
        plan.setApprovalComment(null);
        plan.setApproverId(directLeaderId);
        plan.setApproveAt(null);
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        syncMonthPlanItemStatus(user, plan);
        auditLogService.success(user, "EMPLOYEE_MONTH_PLAN_SUBMIT", "MONTH_PLAN", plan.getId(),
                auditDetail("status", plan.getStatus(), "submittedAt", plan.getSubmitAt(), "approverId", directLeaderId));
        createWorkflowTodo(user, plan.getDeptId(), directLeaderId,
                "MONTH_PLAN_APPROVAL", "月计划待审批", user.realName() + "提交了月计划",
                "MONTH_PLAN", String.valueOf(plan.getId()), LocalDateTime.now().plusDays(2),
                "审批通过或驳回月计划", "影响月度目标生效", "/leader/month-plan-approval");
        return Map.of("id", plan.getId(), "status", toEmployeePlanStatus(plan.getStatus()), "submittedAt", plan.getSubmitAt());
    }

    @Transactional
    public Map<String, Object> withdrawMonthPlan(AuthUser user, Long id) {
        BizMonthPlan plan = requireMonthPlanForUpdate(id);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!PENDING.equals(plan.getStatus())) {
            throw new BizException(409, "只有待审批的月计划可以撤回");
        }
        plan.setStatus(DRAFT);
        plan.setSubmitAt(null);
        plan.setApproverId(null);
        plan.setApproveAt(null);
        plan.setApprovalComment(null);
        plan.setUpdatedBy(user.userId());
        monthPlanMapper.updateById(plan);
        syncMonthPlanItemStatus(user, plan);
        notifyReadTodoReceiversOfWithdrawal(user, "MONTH_PLAN", plan.getId(), "月计划已撤回",
                user.realName() + "撤回了待审批月计划“" + plan.getTitle() + "”。", "/leader/month-plan-approval");
        int closedTodoCount = completeWorkflowTodos("MONTH_PLAN", plan.getId());
        auditLogService.success(user, "EMPLOYEE_MONTH_PLAN_WITHDRAW", "MONTH_PLAN", plan.getId(),
                auditDetail("status", DRAFT, "closedTodoCount", closedTodoCount));
        return Map.of("id", plan.getId(), "status", toEmployeePlanStatus(plan.getStatus()));
    }

    @Transactional
    public Map<String, Object> submitExtraMonthPlanItem(AuthUser user, Long planId,
                                                        EmployeeController.SaveMonthPlanItemReq request) {
        return submitExtraMonthPlanItem(user, planId, request, null);
    }

    @Transactional
    public Map<String, Object> submitExtraMonthPlanItem(AuthUser user, Long planId,
                                                        EmployeeController.SaveMonthPlanItemReq request,
                                                        Long aiReviewId) {
        BizMonthPlan plan = requireMonthPlanForUpdate(planId);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!APPROVED.equals(plan.getStatus())) {
            throw new BizException(409, "常规月计划审批通过后才能新增额外任务");
        }
        validateMonthPlanItem(request, "额外任务");
        requireWritableItemDeadline(plan.getPlanMonth(), request.getDeadline(), "额外任务");
        if (request.getPerformanceWeight() == null
                || request.getPerformanceWeight().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(422, "额外任务绩效权重必须大于0");
        }
        Long directLeaderId = dataScopeService.directLeaderId(plan.getOwnerUserId());
        if (directLeaderId == null) {
            throw new BizException(422, "当前员工未配置直属领导，无法提交额外任务审批");
        }
        if (directLeaderId.equals(plan.getOwnerUserId())) {
            throw new BizException(422, "直属领导不能与计划员工为同一人");
        }

        List<BizMonthPlanItem> existingItems = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, plan.getId())
                .orderByDesc(BizMonthPlanItem::getSortNo)
                .orderByDesc(BizMonthPlanItem::getId));
        int nextSortNo = existingItems.stream()
                .map(BizMonthPlanItem::getSortNo)
                .filter(value -> value != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setMonthPlanId(plan.getId());
        item.setTaskType(EXTRA);
        applyExtraMonthPlanItemRequest(item, request);
        item.setProgress("");
        item.setStatus(PENDING);
        item.setSubmitAt(now);
        item.setApproverId(directLeaderId);
        item.setVersionNo(1);
        item.setSortNo(nextSortNo);
        item.setCreatedBy(user.userId());
        item.setUpdatedBy(user.userId());
        item.setDeleted(0);
        monthPlanItemMapper.insert(item);
        aiReviewService.attachOrRunPlanReview(user, AiReviewService.EXTRA_TASK, item.getId(), aiReviewId);
        auditLogService.success(user, "EMPLOYEE_EXTRA_MONTH_PLAN_ITEM_SUBMIT", "MONTH_PLAN_ITEM", item.getId(),
                auditDetail("monthPlanId", plan.getId(), "taskType", EXTRA,
                        "performanceWeight", item.getPerformanceWeight(), "approverId", directLeaderId));
        createWorkflowTodo(user, plan.getDeptId(), directLeaderId,
                "EXTRA_MONTH_PLAN_ITEM_APPROVAL", "额外月计划任务待审批", user.realName() + "提交了额外月计划任务",
                "MONTH_PLAN_EXTRA_ITEM", String.valueOf(item.getId()), now.plusDays(2),
                "审批通过或驳回额外任务", "审批结果只影响当前额外任务", "/leader/extra-task-approval");
        return toMonthPlanItem(item);
    }

    @Transactional
    public Map<String, Object> withdrawExtraMonthPlanItem(AuthUser user, Long planId, Long itemId) {
        BizMonthPlan plan = requireMonthPlanForUpdate(planId);
        accessService.requireOwner(user, plan.getOwnerUserId());
        BizMonthPlanItem item = requireExtraMonthPlanItemForUpdate(plan, itemId);
        if (!PENDING.equals(item.getStatus())) {
            throw new BizException(409, "只有待审批的额外任务可以撤回");
        }
        item.setStatus(DRAFT);
        item.setSubmitAt(null);
        item.setApproverId(null);
        item.setApproveAt(null);
        item.setApprovalComment(null);
        item.setUpdatedBy(user.userId());
        item.setVersionNo((item.getVersionNo() == null ? 1 : item.getVersionNo()) + 1);
        monthPlanItemMapper.updateById(item);
        notifyReadTodoReceiversOfWithdrawal(user, "MONTH_PLAN_EXTRA_ITEM", item.getId(), "额外任务已撤回",
                user.realName() + "撤回了待审批额外任务“" + item.getTaskName() + "”。", "/leader/extra-task-approval");
        int closedTodoCount = completeWorkflowTodos("MONTH_PLAN_EXTRA_ITEM", item.getId());
        auditLogService.success(user, "EMPLOYEE_EXTRA_MONTH_PLAN_ITEM_WITHDRAW", "MONTH_PLAN_ITEM", item.getId(),
                auditDetail("monthPlanId", plan.getId(), "status", DRAFT, "closedTodoCount", closedTodoCount));
        return toMonthPlanItem(item);
    }

    @Transactional
    public Map<String, Object> saveExtraMonthPlanItemDraft(AuthUser user, Long planId, Long itemId,
                                                           EmployeeController.SaveMonthPlanItemReq request) {
        BizMonthPlan plan = requireMonthPlanForUpdate(planId);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!APPROVED.equals(plan.getStatus())) {
            throw new BizException(409, "常规月计划审批通过后才能编辑额外任务");
        }
        BizMonthPlanItem item = requireExtraMonthPlanItemForUpdate(plan, itemId);
        if (!DRAFT.equals(item.getStatus()) && !REJECTED.equals(item.getStatus())) {
            throw new BizException(409, "只有草稿或已驳回的额外任务可以编辑");
        }
        validateMonthPlanItem(request, "额外任务");
        requirePositivePerformanceWeight(request.getPerformanceWeight(), "额外任务");
        requireWritableItemDeadline(plan.getPlanMonth(), request.getDeadline(), "额外任务");
        applyExtraMonthPlanItemRequest(item, request);
        item.setStatus(DRAFT);
        item.setSubmitAt(null);
        item.setApproverId(null);
        item.setApproveAt(null);
        item.setApprovalComment(null);
        item.setUpdatedBy(user.userId());
        item.setVersionNo((item.getVersionNo() == null ? 1 : item.getVersionNo()) + 1);
        monthPlanItemMapper.updateById(item);
        auditLogService.success(user, "EMPLOYEE_EXTRA_MONTH_PLAN_ITEM_SAVE", "MONTH_PLAN_ITEM", item.getId(),
                auditDetail("monthPlanId", plan.getId(), "status", DRAFT));
        return toMonthPlanItem(item);
    }

    @Transactional
    public Map<String, Object> submitExtraMonthPlanItemDraft(AuthUser user, Long planId, Long itemId) {
        BizMonthPlan plan = requireMonthPlanForUpdate(planId);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!APPROVED.equals(plan.getStatus())) {
            throw new BizException(409, "常规月计划审批通过后才能提交额外任务");
        }
        BizMonthPlanItem item = requireExtraMonthPlanItemForUpdate(plan, itemId);
        if (!DRAFT.equals(item.getStatus()) && !REJECTED.equals(item.getStatus())) {
            throw new BizException(409, "只有草稿或已驳回的额外任务可以提交");
        }
        validateStoredMonthPlanItem(item, "额外任务");
        requirePositivePerformanceWeight(item.getPerformanceWeight(), "额外任务");
        requireWritableItemDeadline(plan.getPlanMonth(), item.getDeadline(), "额外任务");
        aiReviewService.ensurePlanReview(user, AiReviewService.EXTRA_TASK, item.getId());
        Long directLeaderId = requireDirectLeader(plan.getOwnerUserId(), "额外任务");
        LocalDateTime now = LocalDateTime.now();
        item.setStatus(PENDING);
        item.setSubmitAt(now);
        item.setApproverId(directLeaderId);
        item.setApproveAt(null);
        item.setApprovalComment(null);
        item.setUpdatedBy(user.userId());
        item.setVersionNo((item.getVersionNo() == null ? 1 : item.getVersionNo()) + 1);
        monthPlanItemMapper.updateById(item);
        createWorkflowTodo(user, plan.getDeptId(), directLeaderId,
                "EXTRA_MONTH_PLAN_ITEM_APPROVAL", "额外月计划任务待审批", user.realName() + "提交了额外月计划任务",
                "MONTH_PLAN_EXTRA_ITEM", String.valueOf(item.getId()), now.plusDays(2),
                "审批通过或驳回额外任务", "审批结果只影响当前额外任务", "/leader/extra-task-approval");
        auditLogService.success(user, "EMPLOYEE_EXTRA_MONTH_PLAN_ITEM_RESUBMIT", "MONTH_PLAN_ITEM", item.getId(),
                auditDetail("monthPlanId", plan.getId(), "status", PENDING, "approverId", directLeaderId));
        return toMonthPlanItem(item);
    }

    public Map<String, Object> dayPlanDetail(AuthUser user, LocalDate date) {
        BizDayPlan plan = dayPlanMapper.selectOne(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getOwnerUserId, user.userId())
                .eq(BizDayPlan::getPlanDate, date)
                .orderByDesc(BizDayPlan::getId)
                .last("limit 1"));
        if (plan == null) {
            plan = new BizDayPlan();
            plan.setId(0L);
            plan.setPlanDate(date);
            plan.setStatus(DRAFT);
            plan.setContent("");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", plan.getId());
        data.put("planDate", plan.getPlanDate());
        data.put("orgName", dataScopeService.departmentName(plan.getDeptId() == null ? user.deptId() : plan.getDeptId()));
        data.put("relatedMonthPlanItemId", relatedMonthPlanItemId(plan));
        data.put("content", plan.getContent());
        data.put("remark", plan.getRemark() == null ? "" : plan.getRemark());
        data.put("status", toEmployeePlanStatus(plan.getStatus()));
        data.put("reviewStatus", defaultText(plan.getReviewStatus(), "PENDING_COMMENT"));
        data.put("riskLevel", defaultText(plan.getRiskLevel(), "LOW"));
        data.put("leaderComment", plan.getApprovalComment() == null ? "" : plan.getApprovalComment());
        data.put("reviewedAt", plan.getReviewedAt());
        data.put("departmentComment", plan.getDepartmentReviewComment() == null ? "" : plan.getDepartmentReviewComment());
        data.put("departmentReviewedAt", plan.getApproveAt());
        data.put("approvalDueAt", plan.getApprovalDueAt());
        data.put("aiCheckResult", defaultText(plan.getAiCheckResult(), "NORMAL"));
        data.put("monthPlanItemOptions", collectMonthPlanItemOptions(user, date));
        data.put("calendarRule", toWorkdayCalendarItem(workdayCalendarService.resolve(date),
                plan.getId() == null || plan.getId() == 0 ? Set.of() : Set.of(date), LocalDate.now()));
        return data;
    }

    @Transactional
    public Map<String, Object> saveDayPlanDraft(AuthUser user, EmployeeController.SaveDayPlanDraftReq request, boolean submit) {
        requireWritableDayPlanDate(request.getPlanDate());
        BizDayPlan plan = request.getId() == null ? null : dayPlanMapper.selectForUpdateById(request.getId());
        if (request.getId() != null && (plan == null || Integer.valueOf(1).equals(plan.getDeleted()))) {
            throw new BizException(404, "日计划不存在");
        }
        if (plan == null) {
            dataScopeService.lockUser(user.userId());
            requireUniqueDayPlan(user.userId(), request.getPlanDate(), null);
            plan = new BizDayPlan();
            plan.setCreatedBy(user.userId());
            plan.setOwnerUserId(user.userId());
            plan.setDeptId(user.deptId());
            plan.setDeleted(0);
            plan.setStatus(DRAFT);
        } else {
            accessService.requireOwner(user, plan.getOwnerUserId());
            dataScopeService.lockUser(plan.getOwnerUserId());
            if (!DRAFT.equals(plan.getStatus()) && !REJECTED.equals(plan.getStatus())) {
                throw new BizException("只有草稿或驳回状态可编辑");
            }
            requireUniqueDayPlan(plan.getOwnerUserId(), request.getPlanDate(), plan.getId());
        }
        plan.setTitle(buildDayTitle(request.getPlanDate(), request.getContent()));
        plan.setPlanDate(request.getPlanDate());
        plan.setContent(request.getContent());
        plan.setRemark(request.getRemark());
        linkDayPlanToMonthItem(user, plan, request.getRelatedMonthPlanItemId());
        plan.setUpdatedBy(user.userId());
        if (plan.getId() == null) {
            dayPlanMapper.insert(plan);
            auditLogService.success(user, "EMPLOYEE_DAY_PLAN_CREATE", "DAY_PLAN", plan.getId(),
                    auditDetail("planDate", plan.getPlanDate(), "submit", submit));
        } else {
            dayPlanMapper.updateById(plan);
            auditLogService.success(user, "EMPLOYEE_DAY_PLAN_UPDATE", "DAY_PLAN", plan.getId(),
                    auditDetail("planDate", plan.getPlanDate(), "submit", submit));
        }
        if (submit) {
            ReviewVO aiReview = aiReviewService.ensurePlanReview(user, AiReviewService.DAY_PLAN, plan.getId());
            plan.setStatus(PENDING);
            plan.setSubmitAt(LocalDateTime.now());
            plan.setApprovalDueAt(LocalDateTime.now().plusDays(1));
            plan.setReviewStatus("PENDING_COMMENT");
            plan.setRiskLevel("LOW");
            plan.setAiCheckResult(dayPlanAiCheckResult(aiReview));
            plan.setApprovalComment(null);
            plan.setDepartmentReviewComment(null);
            plan.setReviewedBy(null);
            plan.setReviewedAt(null);
            plan.setApproverId(null);
            plan.setApproveAt(null);
            dayPlanMapper.updateById(plan);
            clearPreviousDayPlanReview(plan.getId());
            auditLogService.success(user, "EMPLOYEE_DAY_PLAN_SUBMIT", "DAY_PLAN", plan.getId(),
                    auditDetail("status", plan.getStatus(), "submittedAt", plan.getSubmitAt()));
            createWorkflowTodo(user, plan.getDeptId(), dataScopeService.directLeaderId(user.userId()),
                    "DAY_PLAN_REVIEW", "日计划待点评", user.realName() + "提交了日计划",
                    "DAY_PLAN", String.valueOf(plan.getId()), plan.getApprovalDueAt(),
                    "完成日计划点评或风险标记", "影响日计划闭环", "/leader/daily-review");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", plan.getId());
        data.put("planDate", plan.getPlanDate());
        data.put("relatedMonthPlanItemId", relatedMonthPlanItemId(plan));
        data.put("content", plan.getContent());
        data.put("remark", plan.getRemark() == null ? "" : plan.getRemark());
        data.put("status", toEmployeePlanStatus(plan.getStatus()));
        data.put("monthPlanItemOptions", collectMonthPlanItemOptions(user, plan.getPlanDate()));
        data.put("calendarRule", toWorkdayCalendarItem(workdayCalendarService.resolve(plan.getPlanDate()),
                Set.of(plan.getPlanDate()), LocalDate.now()));
        if (submit) {
            data.put("submittedAt", plan.getSubmitAt());
        }
        return data;
    }

    @Transactional
    public Map<String, Object> withdrawDayPlan(AuthUser user, Long id) {
        BizDayPlan plan = dayPlanMapper.selectForUpdateById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "日计划不存在");
        }
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!PENDING.equals(plan.getStatus())
                || !"PENDING_COMMENT".equals(defaultText(plan.getReviewStatus(), "PENDING_COMMENT"))
                || plan.getReviewedAt() != null) {
            throw new BizException(409, "只有直属领导尚未处理的日计划可以撤回");
        }
        plan.setStatus(DRAFT);
        plan.setSubmitAt(null);
        plan.setApprovalDueAt(null);
        plan.setReviewStatus("PENDING_COMMENT");
        plan.setRiskLevel("LOW");
        plan.setAiCheckResult(null);
        plan.setApprovalComment(null);
        plan.setDepartmentReviewComment(null);
        plan.setReviewedBy(null);
        plan.setReviewedAt(null);
        plan.setApproverId(null);
        plan.setApproveAt(null);
        plan.setUpdatedBy(user.userId());
        dayPlanMapper.updateById(plan);
        notifyReadTodoReceiversOfWithdrawal(user, "DAY_PLAN", plan.getId(), "日计划已撤回",
                user.realName() + "撤回了" + plan.getPlanDate() + "的待点评日计划。", "/leader/daily-review");
        int closedTodoCount = completeWorkflowTodos("DAY_PLAN", plan.getId());
        auditLogService.success(user, "EMPLOYEE_DAY_PLAN_WITHDRAW", "DAY_PLAN", plan.getId(),
                auditDetail("status", DRAFT, "closedTodoCount", closedTodoCount));
        Map<String, Object> result = new HashMap<>();
        result.put("id", plan.getId());
        result.put("planDate", plan.getPlanDate());
        result.put("status", toEmployeePlanStatus(plan.getStatus()));
        return result;
    }

    private void clearPreviousDayPlanReview(Long planId) {
        dayPlanMapper.update(null, new UpdateWrapper<BizDayPlan>()
                .eq("id", planId)
                .set("approval_comment", null)
                .set("department_review_comment", null)
                .set("reviewed_by", null)
                .set("reviewed_at", null)
                .set("approver_id", null)
                .set("approve_at", null));
    }

    public Map<String, Object> resultSubmitOptions(AuthUser user) {
        List<BizMonthPlan> monthPlans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, user.userId())
                .eq(BizMonthPlan::getStatus, APPROVED)
                .orderByDesc(BizMonthPlan::getPlanMonth)
                .orderByDesc(BizMonthPlan::getId));
        Map<String, Object> data = new HashMap<>();
        data.put("monthPlanOptions", monthPlans.stream().map(plan -> Map.of(
                "id", plan.getId(),
                "title", plan.getTitle(),
                "planMonth", plan.getPlanMonth())).toList());
        data.put("monthPlanItemOptions", collectMonthPlanItemOptions(user, null));
        data.put("acceptedFileTypes", List.of("pdf", "doc", "docx", "zip"));
        data.put("maxFileSizeMb", 20);
        data.put("resultVersions", resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                        .eq(BizResult::getDeleted, 0)
                        .eq(BizResult::getOwnerUserId, user.userId())
                        .orderByDesc(BizResult::getId))
                .stream()
                .map(result -> {
                    Map<String, Object> version = new HashMap<>();
                    version.put("id", result.getId());
                    version.put("monthPlanId", result.getPlanId());
                    version.put("monthPlanItemId", result.getMonthPlanItemId());
                    version.put("versionNo", result.getVersionNo());
                    version.put("status", toEmployeeResultStatus(result.getStatus()));
                    version.put("submittedAt", result.getSubmitAt());
                    version.put("leaderSuggestion", defaultText(result.getLeaderSuggestion(), ""));
                    version.put("confirmComment", defaultText(result.getConfirmComment(), ""));
                    return version;
                })
                .toList());
        return data;
    }

    public Map<String, Object> resultDetail(AuthUser user, Long id) {
        BizResult result = resultMapper.selectById(id);
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "成果记录不存在");
        }
        accessService.requireOwner(user, result.getOwnerUserId());

        BizMonthPlan plan = "MONTH".equals(result.getPlanType()) && result.getPlanId() != null
                ? monthPlanMapper.selectById(result.getPlanId()) : null;
        if (plan != null && (!result.getOwnerUserId().equals(plan.getOwnerUserId())
                || Integer.valueOf(1).equals(plan.getDeleted()))) {
            plan = null;
        }
        BizDayPlan dayPlan = "DAY".equals(result.getPlanType()) && result.getPlanId() != null
                ? dayPlanMapper.selectById(result.getPlanId()) : null;
        if (dayPlan != null && (!result.getOwnerUserId().equals(dayPlan.getOwnerUserId())
                || Integer.valueOf(1).equals(dayPlan.getDeleted()))) {
            dayPlan = null;
        }
        BizMonthPlanItem planItem = result.getMonthPlanItemId() == null
                ? null : monthPlanItemMapper.selectById(result.getMonthPlanItemId());
        if (planItem != null && (plan == null || !plan.getId().equals(planItem.getMonthPlanId())
                || Integer.valueOf(1).equals(planItem.getDeleted()))) {
            planItem = null;
        }
        List<BizResultEvidence> evidences = resultEvidenceMapper.selectList(
                new LambdaQueryWrapper<BizResultEvidence>()
                        .eq(BizResultEvidence::getDeleted, 0)
                        .eq(BizResultEvidence::getResultId, result.getId())
                        .orderByAsc(BizResultEvidence::getId));

        Map<String, Object> detail = new HashMap<>();
        detail.put("id", result.getId());
        detail.put("resultNo", "RS-" + result.getId());
        detail.put("title", result.getTitle());
        detail.put("resultDate", result.getResultDate());
        detail.put("description", defaultText(result.getContent(), ""));
        detail.put("completionRate", completionRateOf(result));
        detail.put("versionNo", defaultText(result.getVersionNo(), "V1"));
        detail.put("status", toEmployeeResultStatus(result.getStatus()));
        detail.put("planType", defaultText(result.getPlanType(), "MONTH"));
        detail.put("planId", result.getPlanId());
        detail.put("planTitle", plan != null ? plan.getTitle() : dayPlan == null ? "" : dayPlan.getTitle());
        detail.put("monthPlanItemId", result.getMonthPlanItemId());
        detail.put("planItemName", planItem == null ? "" : planItem.getTaskName());
        detail.put("submittedAt", result.getSubmitAt());
        detail.put("suggestionStatus", defaultText(result.getSuggestionStatus(), "PENDING_SUGGEST"));
        detail.put("leaderSuggestion", defaultText(result.getLeaderSuggestion(), ""));
        detail.put("suggestedAt", result.getSuggestedAt());
        detail.put("confirmComment", defaultText(result.getConfirmComment(), ""));
        detail.put("confirmedAt", result.getConfirmAt());
        detail.put("evidenceStatus", defaultText(result.getEvidenceStatus(), "MISSING"));
        detail.put("issueCodes", jsonCodec.stringList(defaultText(result.getIssueCodes(), "[]")));
        detail.put("issueText", defaultText(result.getIssueText(), ""));
        detail.put("evidences", evidences.stream().map(evidence -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", evidence.getId());
            item.put("fileName", evidence.getFileName());
            item.put("fileType", evidence.getFileType());
            item.put("fileSize", evidence.getFileSize() == null ? 0L : evidence.getFileSize());
            item.put("status", defaultText(evidence.getStatus(), "UPLOADED"));
            item.put("reviewPassed", Boolean.TRUE.equals(evidence.getReviewPassed()));
            item.put("checksum", defaultText(evidence.getChecksum(), ""));
            item.put("createdAt", evidence.getCreatedAt());
            item.put("downloadUrl", "/api/employee/results/" + result.getId() + "/evidence/" + evidence.getId());
            return item;
        }).toList());
        return detail;
    }

    @Transactional
    public Map<String, Object> submitResult(AuthUser user, Long monthPlanId, Long monthPlanItemId,
                                            Integer completionRate, String description, MultipartFile file) {
        return submitResult(user, monthPlanId, monthPlanItemId, completionRate, description, file, null);
    }

    @Transactional
    public Map<String, Object> submitResult(AuthUser user, Long monthPlanId, Long monthPlanItemId,
                                            Integer completionRate, String description, MultipartFile file,
                                            Long aiReviewId) {
        if (completionRate == null || completionRate < 0 || completionRate > 100) {
            throw new BizException(422, "成果完成比例必须在 0 到 100 之间");
        }
        validateEvidenceFile(file);
        EvidenceDocument evidenceDocument = evidenceDocumentService.inspect(file);
        BizMonthPlan plan = requireMonthPlanForUpdate(monthPlanId);
        accessService.requireOwner(user, plan.getOwnerUserId());
        if (!APPROVED.equals(plan.getStatus())) {
            throw new BizException("月计划审批通过后才能提交成果");
        }
        BizMonthPlanItem planItem = null;
        if (monthPlanItemId != null) {
            planItem = requireMonthPlanItem(plan, monthPlanItemId);
        }
        BizResult latest = latestResultForSubmission(user.userId(), monthPlanId, monthPlanItemId);
        if (latest != null && (PENDING.equals(latest.getStatus()) || CONFIRMED.equals(latest.getStatus()))) {
            throw new BizException(409, "当前计划事项已有待确认或已确认成果，不能重复提交");
        }
        BizResult result = new BizResult();
        result.setTitle((planItem == null ? plan.getTitle() : planItem.getTaskName()) + " 成果");
        result.setResultDate(LocalDate.now());
        result.setContent(description == null ? "" : description);
        result.setCompletionRate(completionRate);
        result.setVersionNo(nextResultVersion(monthPlanId, user.userId()));
        result.setPlanType("MONTH");
        result.setPlanId(monthPlanId);
        result.setMonthPlanItemId(monthPlanItemId);
        result.setTemporary(false);
        result.setOwnerUserId(user.userId());
        result.setDeptId(user.deptId());
        result.setStatus(PENDING);
        result.setSubmitAt(LocalDateTime.now());
        result.setEvidenceStatus("COMPLETE");
        result.setAutoLevel(autoLevel(result.getCompletionRate()));
        result.setIssueCodes("[]");
        result.setIssueText("");
        result.setSuggestionStatus("PENDING_SUGGEST");
        result.setCreatedBy(user.userId());
        result.setUpdatedBy(user.userId());
        result.setDeleted(0);
        resultMapper.insert(result);
        ReviewVO aiReview = aiReviewService.attachOrRunResultReview(user, result.getId(), aiReviewId,
                monthPlanId, monthPlanItemId, completionRate, description, evidenceDocument);
        resultEvidenceMapper.insert(saveEvidenceFile(user, result, file));
        // evidence_status is a workflow field: a technically valid uploaded file remains COMPLETE.
        // Semantic sufficiency is stored in the AI review and must not block manual confirmation.
        result.setEvidenceStatus("COMPLETE");
        result.setIssueCodes(jsonCodec.write(aiReview.result().issues().stream()
                .map(issue -> issue.code()).distinct().toList()));
        result.setIssueText(aiReview.result().summary());
        result.setUpdatedBy(user.userId());
        resultMapper.updateById(result);
        auditLogService.success(user, "EMPLOYEE_RESULT_SUBMIT", "RESULT", result.getId(),
                auditDetail("monthPlanId", monthPlanId, "monthPlanItemId", monthPlanItemId,
                        "completionRate", result.getCompletionRate(), "versionNo", result.getVersionNo(),
                        "hasFile", true));
        createWorkflowTodo(user, result.getDeptId(), dataScopeService.directLeaderId(user.userId()),
                "RESULT_SUGGEST", "成果待建议", user.realName() + "提交了成果",
                "RESULT", String.valueOf(result.getId()), LocalDateTime.now().plusDays(1),
                "提交成果确认建议或驳回建议", "影响成果确认时效", "/leader/result-suggest");
        return Map.of(
                "id", result.getId(),
                "versionNo", result.getVersionNo(),
                "status", toEmployeeResultStatus(result.getStatus()),
                "submittedAt", LocalDateTime.now().toString().replace('T', ' ').substring(0, 19)
        );
    }

    public ResponseEntity<Resource> downloadEvidence(AuthUser user, Long resultId, Long evidenceId) {
        BizResult result = resultMapper.selectById(resultId);
        if (result == null || Integer.valueOf(1).equals(result.getDeleted())) {
            throw new BizException(404, "result not found");
        }
        accessService.requireOwner(user, result.getOwnerUserId());
        BizResultEvidence evidence = resultEvidenceMapper.selectById(evidenceId);
        if (evidence == null || Integer.valueOf(1).equals(evidence.getDeleted()) || !resultId.equals(evidence.getResultId())) {
            throw new BizException(404, "evidence not found");
        }
        Path evidencePath = resolveEvidencePath(evidence.getFileUrl());
        Resource resource = new FileSystemResource(evidencePath);
        if (StringUtils.hasText(evidence.getChecksum())
                && !evidence.getChecksum().equalsIgnoreCase(sha256(evidencePath))) {
            throw new BizException(409, "成果证据文件完整性校验失败");
        }
        String fileName = safeEvidenceFileName(evidence.getFileName(), evidence.getId());
        auditLogService.success(user, "EMPLOYEE_RESULT_EVIDENCE_DOWNLOAD", "RESULT_EVIDENCE", evidence.getId(),
                auditDetail("resultId", resultId, "fileName", fileName));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build().toString())
                .body(resource);
    }

    public Map<String, Object> performanceEvidence(AuthUser user, String periodType) {
        String normalizedPeriodType = normalizeEmployeePeriodType(periodType);
        LocalDate[] period = employeePeriod(normalizedPeriodType.toUpperCase(Locale.ROOT), LocalDate.now());
        String startMonth = period[0].format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String endMonth = period[1].format(DateTimeFormatter.ofPattern("yyyy-MM"));
        List<BizMonthPlan> monthPlans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, user.userId())
                .ge(BizMonthPlan::getPlanMonth, startMonth)
                .le(BizMonthPlan::getPlanMonth, endMonth)
                .orderByDesc(BizMonthPlan::getPlanMonth)
                .orderByDesc(BizMonthPlan::getId));
        List<BizDayPlan> dayPlans = dayPlanMapper.selectList(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getOwnerUserId, user.userId())
                .ge(BizDayPlan::getPlanDate, period[0])
                .le(BizDayPlan::getPlanDate, period[1])
                .orderByDesc(BizDayPlan::getPlanDate)
                .orderByDesc(BizDayPlan::getId));
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, user.userId())
                .ge(BizResult::getResultDate, period[0])
                .le(BizResult::getResultDate, period[1])
                .orderByDesc(BizResult::getResultDate)
                .orderByDesc(BizResult::getId));
        List<BizEmployeeAppeal> appeals = appealMapper.selectList(new LambdaQueryWrapper<BizEmployeeAppeal>()
                .eq(BizEmployeeAppeal::getDeleted, 0)
                .eq(BizEmployeeAppeal::getOwnerUserId, user.userId())
                .ge(BizEmployeeAppeal::getCreatedAt, period[0].atStartOfDay())
                .lt(BizEmployeeAppeal::getCreatedAt, period[1].plusDays(1).atStartOfDay())
                .orderByDesc(BizEmployeeAppeal::getCreatedAt)
                .orderByDesc(BizEmployeeAppeal::getId));

        List<Map<String, Object>> items = new ArrayList<>();
        for (BizMonthPlan plan : monthPlans) {
            int score = completionRateOfLatest(plan.getOwnerUserId(), plan.getPlanMonth());
            items.add(performanceEvidenceItem(plan.getId(), LocalDate.parse(plan.getPlanMonth() + "-01"),
                    normalizedPeriodType, "month_plan", plan.getTitle(),
                    "计划状态：" + planStatusText(plan.getStatus()) + "；成果完成率参考值：" + score + "%；"
                            + defaultText(plan.getContent(), "暂无计划说明"),
                    score, plan.getCreatedAt()));
        }
        for (BizDayPlan plan : dayPlans) {
            int score = dayPlanReferenceScore(plan);
            String reviewText = StringUtils.hasText(plan.getReviewStatus())
                    ? "；点评状态：" + reviewStatusText(plan.getReviewStatus())
                    : "";
            String riskText = StringUtils.hasText(plan.getRiskLevel()) && !"NONE".equalsIgnoreCase(plan.getRiskLevel())
                    ? "；风险等级：" + plan.getRiskLevel()
                    : "";
            items.add(performanceEvidenceItem(plan.getId(), plan.getPlanDate(), normalizedPeriodType,
                    "day_plan", plan.getTitle(),
                    "计划状态：" + planStatusText(plan.getStatus()) + reviewText + riskText + "；"
                            + defaultText(plan.getContent(), "暂无日计划说明"),
                    score, plan.getCreatedAt()));
        }
        for (BizResult result : results) {
            int score = completionRateOf(result);
            items.add(performanceEvidenceItem(result.getId(), result.getResultDate(), normalizedPeriodType,
                    "result", result.getTitle(),
                    "成果状态：" + resultStatusText(result.getStatus()) + "；完成比例：" + score + "%；证据状态："
                            + defaultText(result.getEvidenceStatus(), "未记录") + "；"
                            + defaultText(result.getContent(), "暂无成果说明"),
                    score, result.getCreatedAt()));
        }
        for (BizEmployeeAppeal appeal : appeals) {
            int score = appealReferenceScore(appeal);
            LocalDate evidenceDate = appeal.getCreatedAt() == null ? period[0] : appeal.getCreatedAt().toLocalDate();
            String handleText = StringUtils.hasText(appeal.getHandleComment())
                    ? "；处理说明：" + appeal.getHandleComment()
                    : "";
            items.add(performanceEvidenceItem(appeal.getId(), evidenceDate, normalizedPeriodType,
                    "appeal", appeal.getTitle(),
                    "申诉状态：" + appealStatusText(appeal.getStatus()) + "；申诉原因："
                            + defaultText(appeal.getReason(), "未填写") + handleText,
                    score, appeal.getCreatedAt()));
        }
        items.sort((left, right) -> {
            int dateCompare = String.valueOf(right.get("evidenceDate"))
                    .compareTo(String.valueOf(left.get("evidenceDate")));
            if (dateCompare != 0) {
                return dateCompare;
            }
            return String.valueOf(right.get("createdAt")).compareTo(String.valueOf(left.get("createdAt")));
        });

        Map<String, Object> response = new HashMap<>();
        response.put("periodType", normalizedPeriodType);
        response.put("periodStart", period[0]);
        response.put("periodEnd", period[1]);
        response.put("items", items);
        return response;
    }

    @Transactional
    public Map<String, Object> exportPerformanceEvidence(AuthUser user, EmployeeController.EmployeeExportReq request) {
        List<String> formats = request.getFormats().stream()
                .map(format -> format.toUpperCase(Locale.ROOT))
                .map(format -> Set.of("DOC", "DOCX").contains(format) ? "WORD" : format)
                .peek(format -> {
                    if (!Set.of("PDF", "WORD", "ZIP").contains(format)) {
                        throw new BizException("导出格式仅支持 PDF、Word、Zip");
                    }
                })
                .distinct()
                .toList();
        String periodType = StringUtils.hasText(request.getPeriodType()) ? request.getPeriodType().toUpperCase(Locale.ROOT) : "MONTH";
        LocalDate[] period = employeePeriod(periodType, LocalDate.now());
        BizExportTask task = new BizExportTask();
        task.setId("EXP-EMP-" + System.currentTimeMillis());
        task.setDimensionType("PERSON_LEDGER");
        task.setDimensionName("个人绩效依据资料包");
        task.setPeriodType(periodType);
        task.setPeriodStart(period[0]);
        task.setPeriodEnd(period[1]);
        task.setFormats(jsonCodec.write(formats));
        task.setIncludeEvidence(Boolean.TRUE.equals(request.getIncludeEvidence()));
        task.setWatermark("员工、导出时间、周期");
        task.setIntegrityStatus("PENDING_CHECK");
        task.setMissingItems("[]");
        task.setStatus("PROCESSING");
        task.setRequestedBy(user.userId());
        task.setRequestedByName(user.realName());
        task.setRequestedAt(LocalDateTime.now());
        task.setDeptId(user.deptId());
        task.setDeleted(0);
        exportTaskMapper.insert(task);
        try {
            exportFileService.generate(task);
            exportTaskMapper.updateById(task);
        } catch (RuntimeException ex) {
            exportTaskMapper.updateById(task);
            throw ex;
        }
        Map<String, Object> result = new HashMap<>();
        result.put("taskId", task.getId());
        result.put("fileName", task.getFileName());
        result.put("checksum", task.getChecksum());
        result.put("downloadUrl", "/api/employee/export-tasks/" + task.getId() + "/download");
        return result;
    }

    public ResponseEntity<Resource> downloadExport(AuthUser user, String id) {
        BizExportTask task = exportTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted()) || !user.userId().equals(task.getRequestedBy())) {
            throw new BizException(404, "个人导出任务不存在");
        }
        if (!"SUCCESS".equals(task.getStatus()) || !exportFileService.verify(task)) {
            throw new BizException(409, "个人导出文件尚未生成或完整性校验失败");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + task.getFileName() + "\"")
                .body(exportFileService.resource(task));
    }

    public Map<String, Object> appeals(AuthUser user) {
        List<BizEmployeeAppeal> rows = appealMapper.selectList(new LambdaQueryWrapper<BizEmployeeAppeal>()
                .eq(BizEmployeeAppeal::getDeleted, 0)
                .eq(BizEmployeeAppeal::getOwnerUserId, user.userId())
                .orderByDesc(BizEmployeeAppeal::getCreatedAt)
                .orderByDesc(BizEmployeeAppeal::getId));
        return Map.of("items", rows.stream().map(this::toAppealItem).toList());
    }

    public List<Map<String, Object>> appealOptions(AuthUser user) {
        Set<Long> activeResultIds = appealMapper.selectList(new LambdaQueryWrapper<BizEmployeeAppeal>()
                        .eq(BizEmployeeAppeal::getDeleted, 0)
                        .eq(BizEmployeeAppeal::getOwnerUserId, user.userId())
                        .in(BizEmployeeAppeal::getStatus, List.of("SUBMITTED", "PROCESSING")))
                .stream()
                .map(BizEmployeeAppeal::getRelatedResultId)
                .filter(item -> item != null)
                .collect(java.util.stream.Collectors.toSet());
        return resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                        .eq(BizResult::getDeleted, 0)
                        .eq(BizResult::getOwnerUserId, user.userId())
                        .in(BizResult::getStatus, List.of(CONFIRMED, REJECTED))
                        .orderByDesc(BizResult::getUpdatedAt)
                        .orderByDesc(BizResult::getId))
                .stream()
                .filter(this::withinAppealWindow)
                .filter(result -> !activeResultIds.contains(result.getId()))
                .map(result -> {
                    Map<String, Object> option = new HashMap<>();
                    option.put("resultId", result.getId());
                    option.put("label", result.getTitle() + " / " + result.getVersionNo());
                    option.put("status", toEmployeeResultStatus(result.getStatus()));
                    LocalDateTime decisionAt = result.getConfirmAt() == null ? result.getUpdatedAt() : result.getConfirmAt();
                    option.put("deadline", decisionAt == null ? null : decisionAt.plusDays(3));
                    return option;
                })
                .toList();
    }

    @Transactional
    public Map<String, Object> createAppeal(AuthUser user, EmployeeController.CreateAppealReq request) {
        BizResult relatedResult = null;
        if (request.getRelatedResultId() != null) {
            relatedResult = resultMapper.selectForUpdateById(request.getRelatedResultId());
            if (relatedResult == null || Integer.valueOf(1).equals(relatedResult.getDeleted())) {
                throw new BizException(404, "申诉关联成果不存在");
            }
            if (!user.userId().equals(relatedResult.getOwnerUserId())) {
                throw new BizException(403, "只能申诉本人的成果记录");
            }
            if (!Set.of(CONFIRMED, REJECTED).contains(relatedResult.getStatus())) {
                throw new BizException("只有已确认或已驳回的成果可以申诉");
            }
            if (!withinAppealWindow(relatedResult)) {
                throw new BizException("成果确认或驳回超过 3 个自然日，已不能发起申诉");
            }
            Long activeAppealCount = appealMapper.selectCount(new LambdaQueryWrapper<BizEmployeeAppeal>()
                    .eq(BizEmployeeAppeal::getDeleted, 0)
                    .eq(BizEmployeeAppeal::getOwnerUserId, user.userId())
                    .eq(BizEmployeeAppeal::getRelatedResultId, relatedResult.getId())
                    .in(BizEmployeeAppeal::getStatus, List.of("SUBMITTED", "PROCESSING")));
            if (activeAppealCount > 0) {
                throw new BizException(409, "该成果已有处理中申诉，请勿重复提交");
            }
        }
        BizEmployeeAppeal appeal = new BizEmployeeAppeal();
        appeal.setAppealNo("AP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT));
        appeal.setTitle(request.getTitle().trim());
        appeal.setReason(request.getReason().trim());
        appeal.setStatus("SUBMITTED");
        appeal.setOwnerUserId(user.userId());
        appeal.setDeptId(user.deptId());
        appeal.setRelatedResultId(request.getRelatedResultId());
        appeal.setHandlerId(dataScopeService.departmentOwnerId(user.deptId()));
        appeal.setCreatedBy(user.userId());
        appeal.setUpdatedBy(user.userId());
        appeal.setDeleted(0);
        appealMapper.insert(appeal);
        createAppealTodo(user, appeal);
        return toAppealItem(appeal);
    }

    @Transactional
    public Map<String, Object> createPlanAdjustment(AuthUser user, EmployeeController.CreatePlanAdjustmentReq request) {
        if (!"MONTH".equals(request.getPlanType())) {
            throw new BizException("当前员工端仅支持月计划暂停或撤销申请");
        }
        if (request.getPlanId() == null) {
            throw new BizException("请选择需要调整的月计划");
        }
        if (!Set.of("PAUSE", "CANCEL").contains(request.getAdjustmentType())) {
            throw new BizException("调整类型仅支持暂停或撤销");
        }
        BizMonthPlan plan = requireMonthPlanForUpdate(request.getPlanId());
        if (!user.userId().equals(plan.getOwnerUserId())) {
            throw new BizException(403, "只能调整本人的月计划");
        }
        if (!Set.of(PENDING, APPROVED).contains(plan.getStatus())) {
            throw new BizException("只有已提交或已通过的月计划可申请暂停或撤销");
        }
        Long pendingCount = planAdjustmentMapper.selectCount(new LambdaQueryWrapper<BizPlanAdjustment>()
                .eq(BizPlanAdjustment::getDeleted, 0)
                .eq(BizPlanAdjustment::getOriginalPlanType, "MONTH")
                .eq(BizPlanAdjustment::getOriginalPlanId, plan.getId())
                .eq(BizPlanAdjustment::getStatus, "PENDING"));
        if (pendingCount > 0) {
            throw new BizException("该月计划已有待处理的调整申请");
        }
        BizPlanAdjustment adjustment = new BizPlanAdjustment();
        adjustment.setAdjustmentNo("ADJ" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT));
        adjustment.setOriginalPlanType("MONTH");
        adjustment.setOriginalPlanId(plan.getId());
        adjustment.setOriginalPlanNo("MP-" + plan.getPlanMonth() + "-" + plan.getId());
        adjustment.setOriginalWorkContent(plan.getContent());
        adjustment.setOwnerUserId(user.userId());
        adjustment.setDeptId(user.deptId());
        adjustment.setAdjustmentType(request.getAdjustmentType());
        adjustment.setReason(request.getReason().trim());
        adjustment.setImpactText(request.getImpactText());
        adjustment.setStatus("PENDING");
        adjustment.setKeepEvidenceChain(true);
        adjustment.setCreatedBy(user.userId());
        adjustment.setUpdatedBy(user.userId());
        adjustment.setDeleted(0);
        planAdjustmentMapper.insert(adjustment);
        createWorkflowTodo(user, user.deptId(), dataScopeService.directLeaderId(user.userId()),
                "PLAN_ADJUSTMENT", "计划暂停撤销待处理", user.realName() + "提交了计划调整申请",
                "PLAN_ADJUSTMENT", String.valueOf(adjustment.getId()), LocalDateTime.now().plusDays(1),
                "处理暂停或撤销申请", "影响计划执行状态", "/leader/plan-adjust");
        Map<String, Object> result = new HashMap<>();
        result.put("id", adjustment.getId());
        result.put("adjustmentNo", adjustment.getAdjustmentNo());
        result.put("status", adjustment.getStatus());
        return result;
    }

    private BizResultEvidence saveEvidenceFile(AuthUser user, BizResult result, MultipartFile file) {
        String originalName = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String extension = fileType(originalName);
        String storedName = UUID.randomUUID() + "-" + originalName;
        Path relativePath = Paths.get(String.valueOf(result.getId()), storedName);
        Path target = uploadRoot().resolve(relativePath).normalize();
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            registerRollbackCleanup(target);
        } catch (IOException ex) {
            throw new BizException("save evidence failed");
        }
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setResultId(result.getId());
        evidence.setFileName(originalName);
        evidence.setFileUrl(relativePath.toString().replace('\\', '/'));
        evidence.setFileType(extension);
        evidence.setStatus("UPLOADED");
        evidence.setReviewPassed(false);
        try {
            evidence.setFileSize(Files.size(target));
            evidence.setChecksum(sha256(target));
        } catch (IOException ex) {
            throw new BizException("读取成果文件信息失败");
        }
        evidence.setCreatedBy(user.userId());
        evidence.setDeleted(0);
        return evidence;
    }

    private void validateEvidenceFile(MultipartFile file) {
        if (file == null || file.isEmpty() || !StringUtils.hasText(file.getOriginalFilename())) {
            throw new BizException(422, "请上传成果证据文件");
        }
        String originalName = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String extension = fileType(originalName);
        if (!Set.of("pdf", "doc", "docx", "zip").contains(extension)) {
            throw new BizException(422, "成果附件仅支持 PDF、Word、Zip");
        }
        if (file.getSize() > 20L * 1024 * 1024) {
            throw new BizException(422, "成果附件大小不能超过 20MB");
        }
        try {
            byte[] header;
            try (var input = file.getInputStream()) {
                header = input.readNBytes(8);
            }
            boolean valid = switch (extension) {
                case "pdf" -> startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'}) && isPdf(file);
                case "doc" -> startsWith(header, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                        (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
                case "docx" -> isDocx(file);
                case "zip" -> isNonEmptyZip(file);
                default -> false;
            };
            if (!valid) {
                throw new BizException(422, "成果附件内容与文件类型不匹配");
            }
        } catch (IOException ex) {
            throw new BizException(422, "成果附件读取失败");
        }
    }

    private boolean isDocx(MultipartFile file) throws IOException {
        boolean contentTypes = false;
        boolean documentContent = false;
        try (ZipInputStream input = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                contentTypes |= "[Content_Types].xml".equals(name);
                documentContent |= "word/document.xml".equals(name);
                if (contentTypes && documentContent) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPdf(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            return document.getNumberOfPages() > 0 && !document.isEncrypted();
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean isNonEmptyZip(MultipartFile file) throws IOException {
        try (ZipInputStream input = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private Path resolveEvidencePath(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new BizException(404, "evidence file not found");
        }
        try {
            Path root = uploadRoot();
            Path resolved = root.resolve(fileUrl).normalize();
            if (!resolved.startsWith(root) || !Files.isRegularFile(resolved) || !Files.isReadable(resolved)) {
                throw new BizException(404, "evidence file not found");
            }
            Path realRoot = root.toRealPath();
            Path realFile = resolved.toRealPath();
            if (!realFile.startsWith(realRoot) || !Files.isRegularFile(realFile) || !Files.isReadable(realFile)) {
                throw new BizException(404, "evidence file not found");
            }
            return realFile;
        } catch (IOException ex) {
            throw new BizException(404, "evidence file not found");
        }
    }

    private String safeEvidenceFileName(String fileName, Long evidenceId) {
        String fallback = "evidence-" + evidenceId;
        if (!StringUtils.hasText(fileName)) {
            return fallback;
        }
        try {
            Path name = Paths.get(fileName).getFileName();
            return name == null || !StringUtils.hasText(name.toString()) ? fallback : name.toString();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private void registerRollbackCleanup(Path target) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(target);
                    } catch (IOException ignored) {
                        // Best-effort cleanup after transaction rollback.
                    }
                }
            }
        });
    }

    private Path uploadRoot() {
        return Paths.get(uploadRootPath).toAbsolutePath().normalize();
    }

    private BizMonthPlan requireMonthPlan(Long id) {
        BizMonthPlan plan = monthPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "月计划不存在");
        }
        return plan;
    }

    private BizMonthPlan requireMonthPlanForUpdate(Long id) {
        BizMonthPlan plan = monthPlanMapper.selectForUpdateById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getDeleted())) {
            throw new BizException(404, "月计划不存在");
        }
        return plan;
    }

    private String normalizePlanMonth(String planMonth) {
        try {
            return YearMonth.parse(planMonth.trim()).toString();
        } catch (DateTimeException ex) {
            throw new BizException(422, "计划月份格式必须为 yyyy-MM");
        }
    }

    private LocalDate businessToday() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private void requireWritableDayPlanDate(LocalDate planDate) {
        if (planDate == null) {
            throw new BizException(422, "请选择计划日期");
        }
        if (planDate.isBefore(businessToday())) {
            throw new BizException(422, "日计划只能填写今天及以后的日期");
        }
    }

    private void requireWritablePlanMonth(String planMonth) {
        YearMonth month;
        try {
            month = YearMonth.parse(planMonth);
        } catch (DateTimeException | NullPointerException ex) {
            throw new BizException(422, "计划月份格式必须为 yyyy-MM");
        }
        if (month.isBefore(YearMonth.from(businessToday()))) {
            throw new BizException(422, "月计划只能填写当前月份及以后的月份");
        }
    }

    private void requireWritableItemDeadline(String planMonth, LocalDate deadline, String label) {
        if (deadline == null) {
            throw new BizException(422, label + "截止日期不能为空");
        }
        YearMonth month;
        try {
            month = YearMonth.parse(planMonth);
        } catch (DateTimeException | NullPointerException ex) {
            throw new BizException(422, "计划月份格式必须为 yyyy-MM");
        }
        if (deadline.isBefore(businessToday())) {
            throw new BizException(422, label + "截止日期不能早于今天");
        }
        if (!YearMonth.from(deadline).equals(month)) {
            throw new BizException(422, label + "截止日期必须在计划月份内");
        }
    }

    private void requireUniqueMonthPlan(Long ownerUserId, String planMonth, Long excludeId) {
        Long count = monthPlanMapper.selectCount(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, ownerUserId)
                .eq(BizMonthPlan::getPlanMonth, planMonth)
                .ne(BizMonthPlan::getStatus, "CANCELED")
                .ne(excludeId != null, BizMonthPlan::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(409, "该月份已存在月计划，请打开原计划继续编辑");
        }
    }

    private void requireUniqueDayPlan(Long ownerUserId, LocalDate planDate, Long excludeId) {
        Long count = dayPlanMapper.selectCount(new LambdaQueryWrapper<BizDayPlan>()
                .eq(BizDayPlan::getDeleted, 0)
                .eq(BizDayPlan::getOwnerUserId, ownerUserId)
                .eq(BizDayPlan::getPlanDate, planDate)
                .ne(BizDayPlan::getStatus, "CANCELED")
                .ne(excludeId != null, BizDayPlan::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(409, "该日期已存在日计划，请打开原计划继续编辑");
        }
    }

    private BizMonthPlanItem requireMonthPlanItem(Long id) {
        BizMonthPlanItem item = monthPlanItemMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "月计划明细不存在");
        }
        return item;
    }

    private BizMonthPlanItem requireMonthPlanItem(BizMonthPlan plan, Long itemId) {
        BizMonthPlanItem item = requireMonthPlanItem(itemId);
        if (!plan.getId().equals(item.getMonthPlanId())) {
            throw new BizException(400, "月计划事项不存在或不属于当前月计划");
        }
        if (!isEffectiveMonthPlanItem(item)) {
            throw new BizException(422, "额外任务审批通过后才能关联日计划或提交成果");
        }
        return item;
    }

    private BizMonthPlanItem requireExtraMonthPlanItemForUpdate(BizMonthPlan plan, Long itemId) {
        BizMonthPlanItem item = monthPlanItemMapper.selectForUpdateById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(404, "额外任务不存在");
        }
        if (!plan.getId().equals(item.getMonthPlanId()) || !EXTRA.equals(item.getTaskType())) {
            throw new BizException(404, "额外任务不存在或不属于当前月计划");
        }
        return item;
    }

    private Map<String, Object> toMonthPlanCard(BizMonthPlan plan) {
        Map<String, Object> card = new HashMap<>();
        card.put("id", plan.getId());
        card.put("planMonth", plan.getPlanMonth());
        card.put("title", plan.getTitle());
        card.put("planStatus", toEmployeePlanStatus(plan.getStatus()));
        card.put("resultStatus", inferResultStatus(plan.getId(), plan.getOwnerUserId()));
        card.put("completionRate", completionRateOfLatest(plan.getOwnerUserId(), plan.getPlanMonth()));
        card.put("updatedAt", String.valueOf(plan.getUpdatedAt()));
        return card;
    }

    private Map<String, Object> toDayPlanCalendarItem(BizDayPlan plan) {
        Map<String, Object> item = new HashMap<>();
        item.put("date", String.valueOf(plan.getPlanDate()));
        item.put("status", toEmployeePlanStatus(plan.getStatus()));
        return item;
    }

    private Map<String, Object> toWorkdayCalendarItem(CalendarDay rule, Set<LocalDate> planDates, LocalDate today) {
        Map<String, Object> item = new HashMap<>();
        item.put("date", String.valueOf(rule.date()));
        item.put("ruleType", rule.ruleType());
        item.put("forceReport", rule.forceReport());
        item.put("description", rule.description());
        item.put("ruleId", rule.ruleId());
        item.put("versionNo", rule.versionNo());
        item.put("explicit", rule.explicit());
        item.put("missingRequired", rule.forceReport() && !rule.date().isAfter(today) && !planDates.contains(rule.date()));
        return item;
    }

    private List<Map<String, Object>> monthPlanItems(BizMonthPlan plan) {
        List<BizMonthPlanItem> items = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, plan.getId())
                .orderByAsc(BizMonthPlanItem::getSortNo)
                .orderByAsc(BizMonthPlanItem::getId));
        return items.stream().map(this::toMonthPlanItem).toList();
    }

    private Map<String, Object> toMonthPlanItem(BizMonthPlanItem source) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", source.getId());
        item.put("taskType", defaultText(source.getTaskType(), REGULAR));
        item.put("performanceWeight", source.getPerformanceWeight() == null ? BigDecimal.ZERO : source.getPerformanceWeight());
        item.put("taskName", source.getTaskName());
        item.put("taskContent", source.getTaskContent());
        item.put("progress", source.getProgress() == null ? "" : source.getProgress());
        item.put("deliverable", source.getDeliverable());
        item.put("deadline", source.getDeadline());
        item.put("status", toEmployeePlanStatus(source.getStatus()));
        item.put("submittedAt", source.getSubmitAt());
        item.put("approvedAt", source.getApproveAt());
        item.put("approvalComment", defaultText(source.getApprovalComment(), ""));
        item.put("sortNo", source.getSortNo() == null ? 0 : source.getSortNo());
        return item;
    }

    private void replaceMonthPlanItems(AuthUser user, BizMonthPlan plan, List<EmployeeController.SaveMonthPlanItemReq> requestItems) {
        List<BizMonthPlanItem> existingItems = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, plan.getId()));
        Map<Long, BizMonthPlanItem> existingById = new HashMap<>();
        existingItems.forEach(item -> existingById.put(item.getId(), item));
        Set<Long> retainedIds = new HashSet<>();
        int sortNo = 1;
        if (requestItems != null) {
            for (EmployeeController.SaveMonthPlanItemReq requestItem : requestItems) {
                if (requestItem == null) {
                    throw new BizException(422, "月计划明细不能为空");
                }
                BizMonthPlanItem item;
                boolean created;
                if (requestItem.getId() == null) {
                    item = new BizMonthPlanItem();
                    item.setMonthPlanId(plan.getId());
                    item.setCreatedBy(user.userId());
                    created = true;
                } else {
                    item = existingById.get(requestItem.getId());
                    if (item == null) {
                        throw new BizException(400, "月计划明细不存在或不属于当前计划");
                    }
                    if (!retainedIds.add(item.getId())) {
                        throw new BizException(422, "月计划明细不能重复");
                    }
                    if (EXTRA.equals(item.getTaskType())) {
                        throw new BizException(409, "额外任务不能通过常规月计划整表编辑接口修改");
                    }
                    created = false;
                }
                validateMonthPlanItem(requestItem, "月计划明细");
                requirePositivePerformanceWeight(requestItem.getPerformanceWeight(), "月计划明细");
                requireWritableItemDeadline(plan.getPlanMonth(), requestItem.getDeadline(), "月计划明细");
                item.setTaskType(REGULAR);
                item.setPerformanceWeight(requestItem.getPerformanceWeight() == null
                        ? BigDecimal.ZERO : requestItem.getPerformanceWeight());
                item.setTaskName(defaultText(requestItem.getTaskName(), ""));
                item.setTaskContent(defaultText(requestItem.getTaskContent(), ""));
                item.setTarget(null);
                item.setProgress(defaultText(item.getProgress(), ""));
                item.setDeliverable(requestItem.getDeliverable());
                item.setAcceptanceStandard(null);
                item.setEstimatedHours(null);
                item.setDeadline(requestItem.getDeadline());
                item.setCompletionRate(0);
                item.setStatus(plan.getStatus());
                if (created) {
                    item.setVersionNo(1);
                }
                item.setSortNo(sortNo++);
                item.setRemark(null);
                item.setUpdatedBy(user.userId());
                item.setDeleted(0);
                if (created) {
                    monthPlanItemMapper.insert(item);
                    retainedIds.add(item.getId());
                } else {
                    monthPlanItemMapper.updateById(item);
                }
            }
        }
        for (BizMonthPlanItem item : existingItems) {
            if (retainedIds.contains(item.getId())) {
                continue;
            }
            item.setDeleted(1);
            item.setUpdatedBy(user.userId());
            monthPlanItemMapper.updateById(item);
        }
    }

    private void syncMonthPlanItemStatus(AuthUser user, BizMonthPlan plan) {
        List<BizMonthPlanItem> items = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, plan.getId()));
        for (BizMonthPlanItem item : items) {
            item.setStatus(plan.getStatus());
            item.setUpdatedBy(user.userId());
            monthPlanItemMapper.updateById(item);
        }
    }

    private List<Map<String, Object>> confirmRecords(Long monthPlanId) {
        BizMonthPlan plan = requireMonthPlan(monthPlanId);
        List<Map<String, Object>> records = new ArrayList<>();
        if (plan.getSubmitAt() != null) {
            records.add(confirmRecord(-monthPlanId * 10, "month_plan", monthPlanId,
                    dataScopeService.requireUser(plan.getOwnerUserId()).getRealName(), "提交月计划", "进入直属领导审批", plan.getSubmitAt()));
        }
        if (plan.getApproveAt() != null) {
            String operator = plan.getApproverId() == null ? "直属领导" : dataScopeService.requireUser(plan.getApproverId()).getRealName();
            records.add(confirmRecord(-monthPlanId * 10 - 1, "month_plan", monthPlanId, operator,
                    "APPROVED".equals(plan.getStatus()) ? "审批通过" : "审批驳回",
                    defaultText(plan.getApprovalComment(), "无审批意见"), plan.getApproveAt()));
        }
        List<BizMonthPlanItem> extraItems = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, monthPlanId)
                .eq(BizMonthPlanItem::getTaskType, EXTRA)
                .orderByAsc(BizMonthPlanItem::getId));
        for (BizMonthPlanItem item : extraItems) {
            if (item.getSubmitAt() != null) {
                records.add(confirmRecord(item.getId() * 10, "month_plan_extra_item", item.getId(),
                        dataScopeService.requireUser(plan.getOwnerUserId()).getRealName(), "提交额外任务",
                        item.getTaskName() + "（权重 " + item.getPerformanceWeight() + "%）", item.getSubmitAt()));
            }
            if (item.getApproveAt() != null) {
                String operator = item.getApproverId() == null ? "直属领导"
                        : dataScopeService.requireUser(item.getApproverId()).getRealName();
                records.add(confirmRecord(item.getId() * 10 + 1, "month_plan_extra_item", item.getId(), operator,
                        APPROVED.equals(item.getStatus()) ? "额外任务审批通过" : "额外任务审批驳回",
                        defaultText(item.getApprovalComment(), "无审批意见"), item.getApproveAt()));
            }
        }
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getPlanId, monthPlanId)
                .orderByAsc(BizResult::getId));
        for (BizResult result : results) {
            if (result.getSubmitAt() != null) {
                records.add(confirmRecord(result.getId() * 10, "result", result.getId(),
                        dataScopeService.requireUser(result.getOwnerUserId()).getRealName(), "提交成果 " + result.getVersionNo(),
                        result.getContent(), result.getSubmitAt()));
            }
            if (result.getSuggestedAt() != null) {
                String operator = result.getSuggestedBy() == null ? "直属领导" : dataScopeService.requireUser(result.getSuggestedBy()).getRealName();
                records.add(confirmRecord(result.getId() * 10 + 1, "result", result.getId(), operator,
                        "SUGGEST_CONFIRM".equals(result.getSuggestionStatus()) ? "建议确认" : "建议驳回",
                        defaultText(result.getLeaderSuggestion(), "无建议说明"), result.getSuggestedAt()));
            }
            if (result.getConfirmAt() != null) {
                String operator = result.getConfirmerId() == null ? "部门负责人" : dataScopeService.requireUser(result.getConfirmerId()).getRealName();
                records.add(confirmRecord(result.getId() * 10 + 2, "result", result.getId(), operator,
                        "CONFIRMED".equals(result.getStatus()) ? "成果最终确认" : "成果驳回",
                        defaultText(result.getConfirmComment(), "无确认意见"), result.getConfirmAt()));
            }
        }
        records.sort((left, right) -> String.valueOf(right.get("createdAt")).compareTo(String.valueOf(left.get("createdAt"))));
        return records;
    }

    private Map<String, Object> confirmRecord(Long id, String bizType, Long bizId, String operatorName,
                                              String action, String comment, LocalDateTime createdAt) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("bizType", bizType);
        item.put("bizId", bizId);
        item.put("operatorName", operatorName);
        item.put("action", action);
        item.put("comment", comment);
        item.put("createdAt", String.valueOf(createdAt));
        return item;
    }

    private List<Map<String, Object>> deliverables(BizMonthPlan plan) {
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, plan.getOwnerUserId())
                .eq(BizResult::getPlanId, plan.getId())
                .orderByDesc(BizResult::getId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BizResult result : results) {
            List<BizResultEvidence> evidences = resultEvidenceMapper.selectList(new LambdaQueryWrapper<BizResultEvidence>()
                    .eq(BizResultEvidence::getDeleted, 0)
                    .eq(BizResultEvidence::getResultId, result.getId())
                    .orderByDesc(BizResultEvidence::getId));
            for (BizResultEvidence evidence : evidences) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", evidence.getId());
                row.put("name", evidence.getFileName());
                row.put("fileType", evidence.getFileType());
                row.put("relatedTaskName", plan.getTitle());
                row.put("submittedAt", String.valueOf(evidence.getCreatedAt()));
                row.put("fileUrl", "/api/employee/results/" + result.getId() + "/evidence/" + evidence.getId());
                rows.add(row);
            }
        }
        return rows;
    }

    private String fileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 && index + 1 < fileName.length() ? fileName.substring(index + 1).toLowerCase() : "";
    }

    private int itemCount(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private String auditDetail(Object... pairs) {
        Map<String, Object> detail = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            detail.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void validateMonthPlanForSubmit(BizMonthPlan plan) {
        List<BizMonthPlanItem> items = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                .eq(BizMonthPlanItem::getDeleted, 0)
                .eq(BizMonthPlanItem::getMonthPlanId, plan.getId())
                .orderByAsc(BizMonthPlanItem::getSortNo));
        if (items.isEmpty()) {
            throw new BizException("请至少填写一条月计划明细");
        }
        BigDecimal regularWeightTotal = BigDecimal.ZERO;
        for (int index = 0; index < items.size(); index++) {
            BizMonthPlanItem item = items.get(index);
            if (!StringUtils.hasText(item.getTaskName())
                    || !StringUtils.hasText(item.getTaskContent())
                    || !StringUtils.hasText(item.getDeliverable())
                    || item.getDeadline() == null) {
                throw new BizException("第 " + (index + 1) + " 条计划缺少任务名称、任务内容、交付物或截止日期");
            }
            requireWritableItemDeadline(plan.getPlanMonth(), item.getDeadline(), "第 " + (index + 1) + " 条计划");
            String taskType = defaultText(item.getTaskType(), REGULAR);
            if (!REGULAR.equals(taskType)) {
                throw new BizException(422, "常规月计划首次提交时只能包含常规任务");
            }
            if (item.getPerformanceWeight() == null
                    || item.getPerformanceWeight().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(422, "第 " + (index + 1) + " 条计划绩效权重必须大于0");
            }
            regularWeightTotal = regularWeightTotal.add(item.getPerformanceWeight());
        }
        if (regularWeightTotal.compareTo(new BigDecimal("100")) != 0) {
            throw new BizException(422, "常规任务绩效权重合计必须等于100%，当前为" + regularWeightTotal.stripTrailingZeros().toPlainString() + "%");
        }
    }

    private void validateMonthPlanItem(EmployeeController.SaveMonthPlanItemReq item, String label) {
        if (item == null
                || !StringUtils.hasText(item.getTaskName())
                || !StringUtils.hasText(item.getTaskContent())
                || !StringUtils.hasText(item.getDeliverable())
                || item.getDeadline() == null) {
            throw new BizException(422, label + "缺少任务名称、任务内容、交付物或截止日期");
        }
    }

    private void validateStoredMonthPlanItem(BizMonthPlanItem item, String label) {
        if (item == null
                || !StringUtils.hasText(item.getTaskName())
                || !StringUtils.hasText(item.getTaskContent())
                || !StringUtils.hasText(item.getDeliverable())
                || item.getDeadline() == null) {
            throw new BizException(422, label + "缺少任务名称、任务内容、交付物或截止日期");
        }
    }

    private void requirePositivePerformanceWeight(BigDecimal weight, String label) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(422, label + "绩效权重必须大于0");
        }
    }

    private void applyExtraMonthPlanItemRequest(BizMonthPlanItem item,
                                                EmployeeController.SaveMonthPlanItemReq request) {
        item.setPerformanceWeight(request.getPerformanceWeight());
        item.setTaskName(request.getTaskName().trim());
        item.setTaskContent(request.getTaskContent().trim());
        item.setTarget(null);
        item.setDeliverable(request.getDeliverable().trim());
        item.setAcceptanceStandard(null);
        item.setEstimatedHours(null);
        item.setDeadline(request.getDeadline());
        item.setCompletionRate(0);
        item.setRemark(null);
    }

    private Long requireDirectLeader(Long ownerUserId, String objectLabel) {
        Long directLeaderId = dataScopeService.directLeaderId(ownerUserId);
        if (directLeaderId == null) {
            throw new BizException(422, "当前员工未配置直属领导，无法提交" + objectLabel + "审批");
        }
        if (directLeaderId.equals(ownerUserId)) {
            throw new BizException(422, "直属领导不能与计划员工为同一人");
        }
        return directLeaderId;
    }

    private void createAppealTodo(AuthUser user, BizEmployeeAppeal appeal) {
        if (appeal.getHandlerId() == null) {
            return;
        }
        SysUser handler = dataScopeService.requireUser(appeal.getHandlerId());
        BizTodo todo = new BizTodo();
        todo.setSceneCode("APPEAL_PROCESS");
        todo.setTitle("申诉待处理");
        todo.setTriggerText(user.realName() + "提交了绩效申诉");
        todo.setReceiverId(handler.getId());
        todo.setReceiverName(handler.getRealName());
        todo.setObjectType("APPEAL");
        todo.setObjectId(String.valueOf(appeal.getId()));
        todo.setDueAt(LocalDateTime.now().plusDays(3));
        todo.setRequirementText("查看申诉依据并完成处理");
        todo.setImpactText("影响员工绩效闭环");
        todo.setMessageType("TODO");
        todo.setStatus("UNREAD");
        todo.setRemindCount(0);
        todo.setRouteHint("/department/todo");
        todo.setDeptId(user.deptId());
        todo.setCreatedBy(user.userId());
        todo.setUpdatedBy(user.userId());
        todo.setDeleted(0);
        todoMapper.insert(todo);
    }

    private void createWorkflowTodo(AuthUser user, Long deptId, Long receiverId, String sceneCode, String title,
                                    String triggerText, String objectType, String objectId, LocalDateTime dueAt,
                                    String requirement, String impact, String routeHint) {
        if (receiverId == null) {
            return;
        }
        BizTodo existing = todoMapper.selectOne(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getReceiverId, receiverId)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, objectId)
                .ne(BizTodo::getStatus, "DONE")
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        SysUser receiver = dataScopeService.requireUser(receiverId);
        BizTodo todo = new BizTodo();
        todo.setSceneCode(sceneCode);
        todo.setTitle(title);
        todo.setTriggerText(triggerText);
        todo.setReceiverId(receiverId);
        todo.setReceiverName(receiver.getRealName());
        todo.setObjectType(objectType);
        todo.setObjectId(objectId);
        todo.setDueAt(dueAt);
        todo.setRequirementText(requirement);
        todo.setImpactText(impact);
        todo.setMessageType("TODO");
        todo.setStatus("UNREAD");
        todo.setRemindCount(0);
        todo.setRouteHint(routeHint);
        todo.setDeptId(deptId);
        todo.setCreatedBy(user.userId());
        todo.setUpdatedBy(user.userId());
        todo.setDeleted(0);
        todoMapper.insert(todo);
    }

    private int completeWorkflowTodos(String objectType, Long objectId) {
        List<BizTodo> todos = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, String.valueOf(objectId))
                .ne(BizTodo::getStatus, "DONE"));
        LocalDateTime now = LocalDateTime.now();
        for (BizTodo todo : todos) {
            todo.setStatus("DONE");
            todo.setUpdatedAt(now);
            todoMapper.updateById(todo);
        }
        return todos.size();
    }

    private void notifyReadTodoReceiversOfWithdrawal(AuthUser user, String objectType, Long objectId,
                                                      String title, String content, String route) {
        List<BizTodo> readTodos = todoMapper.selectList(new LambdaQueryWrapper<BizTodo>()
                .eq(BizTodo::getDeleted, 0)
                .eq(BizTodo::getObjectType, objectType)
                .eq(BizTodo::getObjectId, String.valueOf(objectId))
                .eq(BizTodo::getStatus, "READ"));
        for (BizTodo todo : readTodos) {
            messageService.createNotice(todo.getReceiverId(), "WORKFLOW_WITHDRAWN", title, content,
                    objectType + "_WITHDRAWN", String.valueOf(objectId), route,
                    todo.getDeptId(), user.userId());
        }
    }

    private void linkDayPlanToMonthItem(AuthUser user, BizDayPlan plan, Long monthPlanItemId) {
        plan.setMonthPlanItemId(monthPlanItemId);
        if (monthPlanItemId == null) {
            plan.setMonthPlanId(null);
            return;
        }
        BizMonthPlanItem item = requireMonthPlanItem(monthPlanItemId);
        BizMonthPlan monthPlan = requireMonthPlanForUpdate(item.getMonthPlanId());
        accessService.requireOwner(user, monthPlan.getOwnerUserId());
        if (!APPROVED.equals(monthPlan.getStatus())) {
            throw new BizException(422, "日计划只能关联已审批通过的月计划事项");
        }
        if (!isEffectiveMonthPlanItem(item)) {
            throw new BizException(422, "日计划只能关联审批通过的额外任务");
        }
        plan.setMonthPlanId(monthPlan.getId());
    }

    private Long relatedMonthPlanItemId(BizDayPlan plan) {
        if (plan.getMonthPlanItemId() != null) {
            return plan.getMonthPlanItemId();
        }
        if (plan.getMonthPlanId() == null) {
            return null;
        }
        BizMonthPlanItem legacyItem = monthPlanItemMapper.selectById(plan.getMonthPlanId());
        return legacyItem == null ? null : legacyItem.getId();
    }

    private String dayPlanAiCheck(BizDayPlan plan) {
        if (!StringUtils.hasText(plan.getContent())) {
            return "REQUIRED_FIELD_MISSING";
        }
        if (plan.getMonthPlanItemId() == null) {
            return "DELIVERABLE_MISSING";
        }
        BizMonthPlanItem item = monthPlanItemMapper.selectById(plan.getMonthPlanItemId());
        if (item == null || !StringUtils.hasText(item.getDeliverable())) {
            return "DELIVERABLE_MISSING";
        }
        return "NORMAL";
    }

    private String dayPlanAiCheckResult(ReviewVO review) {
        if (!"LOW".equals(review.overallRisk())) {
            return review.overallRisk();
        }
        if (!"SUCCESS".equals(review.status())) {
            return "NOT_RUN";
        }
        boolean hasUnknown = review.result() != null && review.result().analysisDimensions() != null
                && review.result().analysisDimensions().stream().anyMatch(item -> "UNKNOWN".equals(item.status()));
        return hasUnknown ? "UNKNOWN" : "NORMAL";
    }

    private String autoLevel(Integer completionRate) {
        int rate = completionRate == null ? 0 : completionRate;
        if (rate >= 100) {
            return "DONE";
        }
        if (rate >= 80) {
            return "BASIC_DONE";
        }
        return "PARTIAL_DONE";
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new BizException("计算成果文件校验值失败");
        }
    }

    private String buildDayTitle(LocalDate date, String content) {
        String base = date == null ? "日计划" : date + " 日计划";
        return StringUtils.hasText(content) ? base : base;
    }

    private String buildMonthTitle(String planMonth, String summary) {
        if (StringUtils.hasText(summary)) {
            String normalized = summary.trim().replaceAll("\\s+", " ");
            return normalized.length() > 40 ? normalized.substring(0, 40) : normalized;
        }
        String month = StringUtils.hasText(planMonth) ? planMonth : LocalDate.now().toString().substring(0, 7);
        return month + " 月计划";
    }

    private List<Map<String, Object>> collectMonthPlanItemOptions(AuthUser user, LocalDate date) {
        List<BizMonthPlan> monthPlans = monthPlanMapper.selectList(new LambdaQueryWrapper<BizMonthPlan>()
                .eq(BizMonthPlan::getDeleted, 0)
                .eq(BizMonthPlan::getOwnerUserId, user.userId())
                .eq(BizMonthPlan::getStatus, APPROVED)
                .orderByDesc(BizMonthPlan::getPlanMonth)
                .orderByDesc(BizMonthPlan::getId));
        List<Map<String, Object>> items = new ArrayList<>();
        for (BizMonthPlan plan : monthPlans) {
            List<BizMonthPlanItem> planItems = monthPlanItemMapper.selectList(new LambdaQueryWrapper<BizMonthPlanItem>()
                    .eq(BizMonthPlanItem::getDeleted, 0)
                    .eq(BizMonthPlanItem::getMonthPlanId, plan.getId())
                    .orderByAsc(BizMonthPlanItem::getSortNo)
                    .orderByAsc(BizMonthPlanItem::getId));
            if (planItems.isEmpty()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", plan.getId());
                item.put("monthPlanId", plan.getId());
                item.put("taskName", plan.getTitle());
                items.add(item);
                continue;
            }
            for (BizMonthPlanItem planItem : planItems.stream().filter(this::isEffectiveMonthPlanItem).toList()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", planItem.getId());
                item.put("monthPlanId", plan.getId());
                item.put("taskName", planItem.getTaskName());
                items.add(item);
            }
        }
        return items;
    }

    private boolean isEffectiveMonthPlanItem(BizMonthPlanItem item) {
        return !EXTRA.equals(defaultText(item.getTaskType(), REGULAR)) || APPROVED.equals(item.getStatus());
    }

    private Map<String, Object> toAppealItem(BizEmployeeAppeal appeal) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", appeal.getId());
        item.put("appealNo", appeal.getAppealNo());
        item.put("title", appeal.getTitle());
        item.put("reason", appeal.getReason());
        item.put("status", toEmployeeAppealStatus(appeal.getStatus()));
        item.put("handleComment", defaultText(appeal.getHandleComment(), ""));
        item.put("handledAt", appeal.getHandledAt());
        item.put("createdAt", String.valueOf(appeal.getCreatedAt()));
        return item;
    }

    private String toEmployeeAppealStatus(String status) {
        if ("DRAFT".equals(status)) {
            return "draft";
        }
        if ("PROCESSING".equals(status)) {
            return "processing";
        }
        if ("RESOLVED".equals(status)) {
            return "resolved";
        }
        if ("CLOSED".equals(status)) {
            return "closed";
        }
        return "submitted";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private boolean withinAppealWindow(BizResult result) {
        LocalDateTime decisionAt = result.getConfirmAt() == null ? result.getUpdatedAt() : result.getConfirmAt();
        return decisionAt != null && !decisionAt.plusDays(3).isBefore(LocalDateTime.now());
    }

    private String normalizeEmployeePeriodType(String periodType) {
        String normalized = StringUtils.hasText(periodType)
                ? periodType.trim().toLowerCase(Locale.ROOT)
                : "month";
        if (!Set.of("day", "week", "month", "quarter", "year").contains(normalized)) {
            throw new BizException("统计周期仅支持日、周、月、季、年");
        }
        return normalized;
    }

    private Map<String, Object> performanceEvidenceItem(Long id, LocalDate evidenceDate, String periodType,
                                                         String sourceType, String title, String description,
                                                         int score, LocalDateTime createdAt) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", id);
        item.put("evidenceDate", evidenceDate);
        item.put("periodType", periodType);
        item.put("sourceType", sourceType);
        item.put("title", defaultText(title, "未命名依据"));
        item.put("description", defaultText(description, "暂无说明"));
        item.put("score", score);
        item.put("createdAt", createdAt == null ? "" : createdAt.format(DISPLAY_DATE_TIME));
        return item;
    }

    private int dayPlanReferenceScore(BizDayPlan plan) {
        if (plan.getMonthPlanItemId() == null) {
            return 0;
        }
        BizMonthPlanItem item = monthPlanItemMapper.selectById(plan.getMonthPlanItemId());
        if (item == null || Integer.valueOf(1).equals(item.getDeleted()) || item.getCompletionRate() == null) {
            return 0;
        }
        return item.getCompletionRate();
    }

    private int appealReferenceScore(BizEmployeeAppeal appeal) {
        if (appeal.getRelatedResultId() == null) {
            return 0;
        }
        BizResult result = resultMapper.selectById(appeal.getRelatedResultId());
        return result == null || Integer.valueOf(1).equals(result.getDeleted()) ? 0 : completionRateOf(result);
    }

    private String planStatusText(String status) {
        return switch (defaultText(status, "DRAFT")) {
            case PENDING -> "待审批";
            case APPROVED -> "已通过";
            case REJECTED -> "已驳回";
            case "PAUSED" -> "已暂停";
            case "CANCELED" -> "已撤销";
            default -> "草稿";
        };
    }

    private String reviewStatusText(String status) {
        return switch (status) {
            case "COMMENTED" -> "已点评";
            case "RISK" -> "已标记风险";
            case "PENDING_REVIEW" -> "待点评";
            default -> status;
        };
    }

    private String resultStatusText(String status) {
        return switch (defaultText(status, DRAFT)) {
            case PENDING -> "待建议";
            case CONFIRMED -> "已确认";
            case REJECTED -> "已驳回";
            default -> "草稿";
        };
    }

    private String appealStatusText(String status) {
        return switch (defaultText(status, "SUBMITTED")) {
            case "PROCESSING" -> "处理中";
            case "RESOLVED" -> "已处理";
            case "CLOSED" -> "已关闭";
            case "DRAFT" -> "草稿";
            default -> "已提交";
        };
    }

    private LocalDate[] employeePeriod(String periodType, LocalDate anchor) {
        return switch (periodType) {
            case "DAY" -> new LocalDate[]{anchor, anchor};
            case "WEEK" -> {
                LocalDate start = anchor.minusDays(anchor.getDayOfWeek().getValue() - 1L);
                yield new LocalDate[]{start, start.plusDays(6)};
            }
            case "QUARTER" -> {
                int startMonth = ((anchor.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(anchor.getYear(), startMonth, 1);
                yield new LocalDate[]{start, start.plusMonths(3).minusDays(1)};
            }
            case "YEAR" -> new LocalDate[]{LocalDate.of(anchor.getYear(), 1, 1), LocalDate.of(anchor.getYear(), 12, 31)};
            default -> {
                LocalDate start = anchor.withDayOfMonth(1);
                yield new LocalDate[]{start, start.plusMonths(1).minusDays(1)};
            }
        };
    }

    private String toEmployeePlanStatus(String status) {
        if (PENDING.equals(status)) {
            return "submitted";
        }
        if (APPROVED.equals(status)) {
            return "approved";
        }
        if (REJECTED.equals(status)) {
            return "rejected";
        }
        if ("PAUSED".equals(status)) {
            return "paused";
        }
        if ("CANCELED".equals(status)) {
            return "canceled";
        }
        return "draft";
    }

    private String toEmployeeResultStatus(String status) {
        if (PENDING.equals(status)) {
            return "submitted";
        }
        if (CONFIRMED.equals(status)) {
            return "confirmed";
        }
        if (REJECTED.equals(status)) {
            return "rejected";
        }
        return "draft";
    }

    private String inferResultStatus(Long planId, Long ownerUserId) {
        List<BizResult> results = resultMapper.selectList(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .eq(BizResult::getPlanId, planId)
                .orderByDesc(BizResult::getId));
        Map<String, BizResult> latestByItem = new HashMap<>();
        for (BizResult result : results) {
            String itemKey = result.getMonthPlanItemId() == null ? "PLAN" : String.valueOf(result.getMonthPlanItemId());
            latestByItem.putIfAbsent(itemKey, result);
        }
        if (latestByItem.isEmpty()) {
            return "not_submitted";
        }
        if (latestByItem.values().stream().anyMatch(result -> PENDING.equals(result.getStatus()))) {
            return "submitted";
        }
        if (latestByItem.values().stream().anyMatch(result -> REJECTED.equals(result.getStatus()))) {
            return "rejected";
        }
        return latestByItem.values().stream().allMatch(result -> CONFIRMED.equals(result.getStatus()))
                ? "confirmed" : "submitted";
    }

    private BizResult latestResultForSubmission(Long ownerUserId, Long monthPlanId, Long monthPlanItemId) {
        LambdaQueryWrapper<BizResult> query = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .eq(BizResult::getPlanId, monthPlanId)
                .orderByDesc(BizResult::getId)
                .last("limit 1");
        if (monthPlanItemId == null) {
            query.isNull(BizResult::getMonthPlanItemId);
        } else {
            query.eq(BizResult::getMonthPlanItemId, monthPlanItemId);
        }
        return resultMapper.selectOne(query);
    }

    private int countResults(Long ownerUserId, String planMonth) {
        LambdaQueryWrapper<BizResult> query = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId);
        addMonthRange(query, planMonth);
        return Math.toIntExact(resultMapper.selectCount(query));
    }

    private int countConfirmedResults(Long ownerUserId, String planMonth) {
        LambdaQueryWrapper<BizResult> query = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .eq(BizResult::getStatus, CONFIRMED);
        addMonthRange(query, planMonth);
        return Math.toIntExact(resultMapper.selectCount(query));
    }

    private int countRejectedResults(Long ownerUserId, String planMonth) {
        LambdaQueryWrapper<BizResult> query = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .eq(BizResult::getStatus, REJECTED);
        addMonthRange(query, planMonth);
        return Math.toIntExact(resultMapper.selectCount(query));
    }

    private String latestResultVersion(Long ownerUserId, String planMonth) {
        LambdaQueryWrapper<BizResult> query = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .orderByDesc(BizResult::getId)
                .last("limit 1");
        addMonthRange(query, planMonth);
        BizResult latest = resultMapper.selectOne(query);
        return latest == null ? "V0" : latest.getVersionNo();
    }

    private int completionRateOfLatest(Long ownerUserId, String planMonth) {
        LambdaQueryWrapper<BizResult> query = new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .orderByDesc(BizResult::getId)
                .last("limit 1");
        addMonthRange(query, planMonth);
        BizResult latest = resultMapper.selectOne(query);
        return latest == null || latest.getCompletionRate() == null ? 0 : latest.getCompletionRate();
    }

    private int completionRateOf(BizResult result) {
        return result.getCompletionRate() == null ? 0 : result.getCompletionRate();
    }

    private String nextResultVersion(Long monthPlanId, Long ownerUserId) {
        Long count = resultMapper.selectCount(new LambdaQueryWrapper<BizResult>()
                .eq(BizResult::getDeleted, 0)
                .eq(BizResult::getOwnerUserId, ownerUserId)
                .eq(BizResult::getPlanType, "MONTH")
                .eq(BizResult::getPlanId, monthPlanId));
        return "V" + (count + 1);
    }

    private void addMonthRange(LambdaQueryWrapper<BizResult> query, String planMonth) {
        if (!StringUtils.hasText(planMonth)) {
            return;
        }
        LocalDate start = LocalDate.parse(planMonth + "-01");
        query.ge(BizResult::getResultDate, start);
        query.le(BizResult::getResultDate, start.plusMonths(1).minusDays(1));
    }
}
