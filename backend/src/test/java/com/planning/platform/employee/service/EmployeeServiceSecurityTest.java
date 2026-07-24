package com.planning.platform.employee.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.planning.platform.ai.model.AiReviewModels.ReviewResult;
import com.planning.platform.ai.model.AiReviewModels.ReviewVO;
import com.planning.platform.ai.service.AiReviewService;
import com.planning.platform.ai.service.EvidenceDocumentService;
import com.planning.platform.ai.service.EvidenceDocumentService.EvidenceDocument;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.employee.controller.EmployeeController;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.service.ExportFileService;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.performance.service.PerformanceJsonCodec;
import com.planning.platform.planning.domain.BizResult;
import com.planning.platform.planning.domain.BizResultEvidence;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.domain.BizDayPlan;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizEmployeeAppealMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.planning.service.PlanningAccessService;
import com.planning.platform.system.mapper.SysAuditLogMapper;
import com.planning.platform.system.service.AuditLogService;
import com.planning.platform.system.service.WorkdayCalendarService;
import com.planning.platform.system.service.WorkdayCalendarService.CalendarDay;
import com.planning.platform.system.domain.SysUser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceSecurityTest {

    @Mock
    private BizMonthPlanMapper monthPlanMapper;
    @Mock
    private BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock
    private BizDayPlanMapper dayPlanMapper;
    @Mock
    private BizResultMapper resultMapper;
    @Mock
    private BizResultEvidenceMapper resultEvidenceMapper;
    @Mock
    private BizEmployeeAppealMapper appealMapper;
    @Mock
    private SysAuditLogMapper auditLogMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PlanningAccessService accessService;
    @Mock
    private PerformanceDataScopeService dataScopeService;
    @Mock
    private BizTodoMapper todoMapper;
    @Mock
    private BizPlanAdjustmentMapper planAdjustmentMapper;
    @Mock
    private BizExportTaskMapper exportTaskMapper;
    @Mock
    private ExportFileService exportFileService;
    @Mock
    private PerformanceJsonCodec jsonCodec;
    @Mock
    private WorkdayCalendarService workdayCalendarService;
    @Mock
    private UserMessageService messageService;
    @Mock
    private AiReviewService aiReviewService;
    @Mock
    private EvidenceDocumentService evidenceDocumentService;

    @InjectMocks
    private EmployeeService employeeService;

    @TempDir
    Path uploadRoot;

    private final AuthUser employee = new AuthUser(10L, "employee", "员工", 110L, 110L,
            false, List.of("EMPLOYEE"), List.of());

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(employeeService, "uploadRootPath", uploadRoot.toString());
        org.mockito.Mockito.lenient().when(monthPlanMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> monthPlanMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(dayPlanMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> dayPlanMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(monthPlanItemMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> monthPlanItemMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(resultMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> resultMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(workdayCalendarService.resolve(any()))
                .thenAnswer(invocation -> new CalendarDay(invocation.getArgument(0), "WORKDAY", true,
                        "默认工作日", null, null, false));
        ReviewResult reviewResult = new ReviewResult("LOW", "检查通过", List.of(), List.of(), List.of(),
                90, 100, "SUFFICIENT", 100, "测试计算依据");
        ReviewVO review = new ReviewVO(1L, "RESULT", 1L, "1", "hash", "RULE_ONLY", "LOW",
                "qwen", "qwen-plus", "v1", LocalDateTime.now(), false, false, null, reviewResult);
        org.mockito.Mockito.lenient().when(aiReviewService.ensurePlanReview(any(), any(), any())).thenReturn(review);
        org.mockito.Mockito.lenient().when(aiReviewService.attachOrRunResultReview(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(review);
        org.mockito.Mockito.lenient().when(evidenceDocumentService.inspect(any()))
                .thenReturn(new EvidenceDocument("evidence.pdf", "pdf", 1L, "checksum", "证据内容", false, true));
        org.mockito.Mockito.lenient().when(jsonCodec.write(any())).thenReturn("[]");
    }

    @Test
    void resultCompletionRateMustStayWithinPercentageRange() {
        BizException error = catchThrowableOfType(
                () -> employeeService.submitResult(employee, 1L, null, 101, "成果", null),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("0 到 100");
        verifyNoInteractions(monthPlanMapper, resultMapper);
    }

    @Test
    void duplicateMonthPlanDraftIsRejected() {
        when(monthPlanMapper.selectCount(any())).thenReturn(1L);
        EmployeeController.SaveMonthPlanDraftReq request = new EmployeeController.SaveMonthPlanDraftReq();
        request.setPlanMonth(YearMonth.now(ZoneId.of("Asia/Shanghai")).toString());
        request.setSummary("重复月计划");

        BizException error = catchThrowableOfType(
                () -> employeeService.createMonthPlanDraft(employee, request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("已存在月计划");
        verify(dataScopeService).lockUser(employee.userId());
        verify(monthPlanMapper, never()).insert(any(BizMonthPlan.class));
    }

    @Test
    void pastMonthPlanDraftIsRejectedBeforeWritingData() {
        EmployeeController.SaveMonthPlanDraftReq request = new EmployeeController.SaveMonthPlanDraftReq();
        request.setPlanMonth(YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).toString());
        request.setSummary("过去月份计划");

        BizException error = catchThrowableOfType(
                () -> employeeService.createMonthPlanDraft(employee, request), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("当前月份及以后");
        verifyNoInteractions(monthPlanMapper);
    }

    @Test
    void pendingMonthPlanCanBeWithdrawnAndClosesItsTodo() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(70L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("PENDING");
        plan.setSubmitAt(LocalDateTime.now());
        plan.setApproverId(20L);
        plan.setDeleted(0);
        BizTodo todo = new BizTodo();
        todo.setId(701L);
        todo.setStatus("UNREAD");
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of());
        when(todoMapper.selectList(any())).thenReturn(List.of(todo));

        Map<String, Object> result = employeeService.withdrawMonthPlan(employee, plan.getId());

        assertThat(result.get("status")).isEqualTo("draft");
        assertThat(plan.getSubmitAt()).isNull();
        assertThat(plan.getApproverId()).isNull();
        assertThat(todo.getStatus()).isEqualTo("DONE");
        verify(monthPlanMapper).updateById(plan);
        verify(todoMapper).updateById(todo);
    }

    @Test
    void existingMonthPlanItemIsUpdatedWithoutChangingItsId() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(7L);
        plan.setStatus("DRAFT");
        plan.setPlanMonth(YearMonth.now(ZoneId.of("Asia/Shanghai")).toString());
        BizMonthPlanItem existing = new BizMonthPlanItem();
        existing.setId(71L);
        existing.setMonthPlanId(plan.getId());
        existing.setTaskName("原任务");
        existing.setDeleted(0);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(existing));

        EmployeeController.SaveMonthPlanItemReq request = new EmployeeController.SaveMonthPlanItemReq();
        request.setId(existing.getId());
        request.setTaskName("更新后的任务");
        request.setTaskContent("保持同一条明细记录");
        request.setDeliverable("保存结果");
        request.setDeadline(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        request.setPerformanceWeight(new BigDecimal("100"));

        ReflectionTestUtils.invokeMethod(employeeService, "replaceMonthPlanItems", employee, plan, List.of(request));

        assertThat(existing.getId()).isEqualTo(71L);
        assertThat(existing.getTaskName()).isEqualTo("更新后的任务");
        assertThat(existing.getSortNo()).isEqualTo(1);
        assertThat(existing.getDeleted()).isZero();
        verify(monthPlanItemMapper).updateById(existing);
        verify(monthPlanItemMapper, never()).insert(any(BizMonthPlanItem.class));
    }

    @Test
    void monthPlanItemFromAnotherPlanCannotBeUpdated() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(7L);
        plan.setStatus("DRAFT");
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of());

        EmployeeController.SaveMonthPlanItemReq request = new EmployeeController.SaveMonthPlanItemReq();
        request.setId(99L);
        request.setTaskName("其他计划的明细");

        BizException error = catchThrowableOfType(
                () -> ReflectionTestUtils.invokeMethod(
                        employeeService, "replaceMonthPlanItems", employee, plan, List.of(request)),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(400);
        assertThat(error.getMessage()).contains("不属于当前计划");
        verify(monthPlanItemMapper, never()).insert(any(BizMonthPlanItem.class));
        verify(monthPlanItemMapper, never()).updateById(any(BizMonthPlanItem.class));
    }

    @Test
    void duplicateDayPlanDraftIsRejected() {
        when(dayPlanMapper.selectCount(any())).thenReturn(1L);
        EmployeeController.SaveDayPlanDraftReq request = new EmployeeController.SaveDayPlanDraftReq();
        request.setPlanDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        request.setContent("重复日计划");

        BizException error = catchThrowableOfType(
                () -> employeeService.saveDayPlanDraft(employee, request, false),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("已存在日计划");
        verify(dataScopeService).lockUser(employee.userId());
        verify(dayPlanMapper, never()).insert(any(BizDayPlan.class));
    }

    @Test
    void pastDayPlanIsRejectedBeforeWritingData() {
        EmployeeController.SaveDayPlanDraftReq request = new EmployeeController.SaveDayPlanDraftReq();
        request.setPlanDate(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1));
        request.setContent("补写昨天计划");

        BizException error = catchThrowableOfType(
                () -> employeeService.saveDayPlanDraft(employee, request, false), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("今天及以后");
        verifyNoInteractions(dayPlanMapper);
    }

    @Test
    void pendingDayPlanCanBeWithdrawnAndClosesItsTodo() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(80L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("PENDING");
        plan.setSubmitAt(LocalDateTime.now());
        plan.setApproverId(20L);
        plan.setDeleted(0);
        BizTodo todo = new BizTodo();
        todo.setId(801L);
        todo.setStatus("READ");
        when(dayPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(todoMapper.selectList(any())).thenReturn(List.of(todo));

        Map<String, Object> result = employeeService.withdrawDayPlan(employee, plan.getId());

        assertThat(result.get("status")).isEqualTo("draft");
        assertThat(plan.getSubmitAt()).isNull();
        assertThat(plan.getApproverId()).isNull();
        assertThat(todo.getStatus()).isEqualTo("DONE");
        verify(dayPlanMapper).updateById(plan);
        verify(todoMapper).updateById(todo);
    }

    @Test
    void riskMarkedDayPlanCannotBeWithdrawnAfterLeaderHandledIt() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(81L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("PENDING");
        plan.setReviewStatus("RISK_MARKED");
        plan.setReviewedAt(LocalDateTime.now());
        plan.setDeleted(0);
        when(dayPlanMapper.selectById(plan.getId())).thenReturn(plan);

        BizException error = catchThrowableOfType(
                () -> employeeService.withdrawDayPlan(employee, plan.getId()), BizException.class);

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("尚未处理");
        verify(dayPlanMapper, never()).updateById(plan);
        verifyNoInteractions(todoMapper);
    }

    @Test
    void dayPlanCannotLinkUnapprovedMonthPlanItem() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(7L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("DRAFT");
        plan.setDeleted(0);
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(71L);
        item.setMonthPlanId(plan.getId());
        item.setDeleted(0);
        when(monthPlanItemMapper.selectById(item.getId())).thenReturn(item);
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);

        EmployeeController.SaveDayPlanDraftReq request = new EmployeeController.SaveDayPlanDraftReq();
        request.setPlanDate(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
        request.setRelatedMonthPlanItemId(item.getId());
        request.setContent("尝试关联草稿月计划");

        BizException error = catchThrowableOfType(
                () -> employeeService.saveDayPlanDraft(employee, request, false),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        verify(dayPlanMapper, never()).insert(any(BizDayPlan.class));
    }

    @Test
    void dayPlanCannotLinkPendingExtraMonthPlanItem() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(8L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(81L);
        item.setMonthPlanId(plan.getId());
        item.setTaskType("EXTRA");
        item.setStatus("PENDING");
        item.setDeleted(0);
        when(monthPlanItemMapper.selectById(item.getId())).thenReturn(item);
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        EmployeeController.SaveDayPlanDraftReq request = new EmployeeController.SaveDayPlanDraftReq();
        request.setPlanDate(LocalDate.now().plusDays(1));
        request.setRelatedMonthPlanItemId(item.getId());
        request.setContent("尝试关联待审批额外任务");

        BizException error = catchThrowableOfType(
                () -> employeeService.saveDayPlanDraft(employee, request, false), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("审批通过");
        verify(dayPlanMapper, never()).insert(any(BizDayPlan.class));
    }

    @Test
    void unknownDayPlanIdCannotCreateAnotherRecord() {
        EmployeeController.SaveDayPlanDraftReq request = new EmployeeController.SaveDayPlanDraftReq();
        request.setId(999L);
        request.setPlanDate(LocalDate.now(ZoneId.of("Asia/Shanghai")));
        request.setContent("不存在的记录");

        BizException error = catchThrowableOfType(
                () -> employeeService.saveDayPlanDraft(employee, request, false),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(404);
        assertThat(error.getMessage()).contains("日计划不存在");
        verify(dayPlanMapper, never()).insert(any(BizDayPlan.class));
    }

    @Test
    void dashboardResultMetricsOnlyUseSelectedMonthPlans() {
        BizMonthPlan currentPlan = new BizMonthPlan();
        currentPlan.setId(1L);
        currentPlan.setOwnerUserId(employee.userId());
        currentPlan.setPlanMonth("2026-07");
        currentPlan.setTitle("七月计划");
        currentPlan.setStatus("APPROVED");
        currentPlan.setDeleted(0);
        BizResult currentResult = new BizResult();
        currentResult.setId(11L);
        currentResult.setOwnerUserId(employee.userId());
        currentResult.setPlanId(currentPlan.getId());
        currentResult.setStatus("PENDING");
        currentResult.setCompletionRate(80);
        currentResult.setDeleted(0);
        BizResult otherMonthResult = new BizResult();
        otherMonthResult.setId(12L);
        otherMonthResult.setOwnerUserId(employee.userId());
        otherMonthResult.setPlanId(2L);
        otherMonthResult.setStatus("PENDING");
        otherMonthResult.setCompletionRate(20);
        otherMonthResult.setDeleted(0);
        when(monthPlanMapper.selectList(any())).thenReturn(List.of(currentPlan));
        when(dayPlanMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectList(any()))
                .thenReturn(List.of(currentResult, otherMonthResult), List.of(currentResult));
        when(resultMapper.selectOne(any())).thenReturn(currentResult);
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(dataScopeService.departmentName(employee.deptId())).thenReturn("产品一组");

        Map<String, Object> dashboard = employeeService.dashboard(employee, "2026-07");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) dashboard.get("summary");
        assertThat(summary.get("submittedResultCount")).isEqualTo(1);
        assertThat(summary.get("averageCompletionRate")).isEqualTo(80);
    }

    @Test
    void dashboardRejectsInvalidMonthBeforeQueryingData() {
        BizException error = catchThrowableOfType(
                () -> employeeService.dashboard(employee, "2026-13"),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        verifyNoInteractions(monthPlanMapper, dayPlanMapper, resultMapper, appealMapper);
    }

    @Test
    void resubmittedDayPlanExplicitlyClearsPreviousReviewFields() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(4L);
        plan.setOwnerUserId(employee.userId());
        plan.setDeptId(employee.deptId());
        plan.setStatus("REJECTED");
        plan.setReviewStatus("SUPPLEMENT_REQUIRED");
        plan.setApprovalComment("旧领导意见");
        plan.setDepartmentReviewComment("旧部门意见");
        plan.setReviewedBy(20L);
        plan.setReviewedAt(LocalDateTime.now().minusDays(1));
        plan.setApproverId(30L);
        plan.setApproveAt(LocalDateTime.now().minusHours(12));
        plan.setDeleted(0);
        SysUser leader = new SysUser();
        leader.setId(20L);
        leader.setRealName("直属领导");
        when(dayPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(dataScopeService.directLeaderId(employee.userId())).thenReturn(leader.getId());
        when(dataScopeService.requireUser(leader.getId())).thenReturn(leader);

        EmployeeController.SaveDayPlanDraftReq request = new EmployeeController.SaveDayPlanDraftReq();
        request.setId(plan.getId());
        request.setPlanDate(LocalDate.now());
        request.setContent("补充风险应对措施和验收节点");
        request.setRemark("已按要求补充");

        employeeService.saveDayPlanDraft(employee, request, true);

        assertThat(plan.getStatus()).isEqualTo("PENDING");
        assertThat(plan.getReviewStatus()).isEqualTo("PENDING_COMMENT");
        assertThat(plan.getApprovalComment()).isNull();
        assertThat(plan.getDepartmentReviewComment()).isNull();
        ArgumentCaptor<UpdateWrapper<BizDayPlan>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(dayPlanMapper).update(isNull(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSet())
                .contains("approval_comment", "department_review_comment", "reviewed_by", "approve_at");
    }

    @Test
    void resultSubmissionRequiresEvidenceFile() {
        BizException error = catchThrowableOfType(
                () -> employeeService.submitResult(employee, 1L, null, 100, "成果", null),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("上传成果证据");
        verifyNoInteractions(monthPlanMapper, resultMapper);
    }

    @Test
    void resubmittedMonthPlanClearsPreviousApprovalFields() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(5L);
        plan.setOwnerUserId(employee.userId());
        plan.setDeptId(employee.deptId());
        plan.setStatus("REJECTED");
        plan.setVersionNo(1);
        plan.setApprovalComment("上一轮驳回意见");
        plan.setApproverId(30L);
        plan.setApproveAt(LocalDateTime.now().minusDays(1));
        plan.setDeleted(0);
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(51L);
        item.setMonthPlanId(plan.getId());
        item.setTaskName("完成接口联调");
        item.setTaskContent("联调员工与领导流程");
        item.setTarget("流程闭环");
        item.setDeliverable("联调记录");
        item.setAcceptanceStandard("关键流程通过");
        item.setEstimatedHours(new BigDecimal("8"));
        item.setDeadline(LocalDate.now().plusDays(5));
        plan.setPlanMonth(YearMonth.from(item.getDeadline()).toString());
        item.setCompletionRate(0);
        item.setTaskType("REGULAR");
        item.setPerformanceWeight(new BigDecimal("100"));
        item.setDeleted(0);
        SysUser directLeader = new SysUser();
        directLeader.setId(20L);
        directLeader.setRealName("直属领导");
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(item));
        when(dataScopeService.directLeaderId(plan.getOwnerUserId())).thenReturn(directLeader.getId());
        when(dataScopeService.requireUser(directLeader.getId())).thenReturn(directLeader);

        employeeService.submitMonthPlan(employee, plan.getId());

        assertThat(plan.getStatus()).isEqualTo("PENDING");
        assertThat(plan.getVersionNo()).isEqualTo(2);
        assertThat(plan.getApprovalComment()).isNull();
        assertThat(plan.getApproverId()).isEqualTo(directLeader.getId());
        assertThat(plan.getApproveAt()).isNull();
        verify(monthPlanMapper).updateById(plan);
        ArgumentCaptor<BizTodo> todo = ArgumentCaptor.forClass(BizTodo.class);
        verify(todoMapper).insert(todo.capture());
        assertThat(todo.getValue().getReceiverId()).isEqualTo(directLeader.getId());
        assertThat(todo.getValue().getRouteHint()).isEqualTo("/leader/month-plan-approval");
    }

    @Test
    void monthPlanSubmitIgnoresLegacyEstimatedHoursField() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(55L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("DRAFT");
        plan.setDeleted(0);
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(551L);
        item.setMonthPlanId(plan.getId());
        item.setTaskName("超量工时任务");
        item.setTaskContent("检查服务端范围校验");
        item.setTarget("拒绝异常工时");
        item.setDeliverable("校验记录");
        item.setAcceptanceStandard("接口返回明确校验错误");
        item.setEstimatedHours(new BigDecimal("800"));
        item.setDeadline(LocalDate.now().plusDays(5));
        plan.setPlanMonth(YearMonth.from(item.getDeadline()).toString());
        item.setCompletionRate(0);
        item.setTaskType("REGULAR");
        item.setPerformanceWeight(new BigDecimal("100"));
        item.setDeleted(0);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(item));

        ReflectionTestUtils.invokeMethod(employeeService, "validateMonthPlanForSubmit", plan);
    }

    @Test
    void monthPlanSubmitRequiresRegularWeightTotalOfOneHundred() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(56L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("DRAFT");
        plan.setDeleted(0);
        BizMonthPlanItem item = completeMonthPlanItem(561L, plan.getId(), new BigDecimal("80"));
        plan.setPlanMonth(YearMonth.from(item.getDeadline()).toString());
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(item));

        BizException error = catchThrowableOfType(
                () -> employeeService.submitMonthPlan(employee, plan.getId()), BizException.class);

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("合计必须等于100%").contains("80%");
        verify(monthPlanMapper, never()).updateById(plan);
    }

    @Test
    void approvedMonthPlanCanSubmitIndependentExtraTaskToDirectLeader() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(57L);
        plan.setOwnerUserId(employee.userId());
        plan.setDeptId(employee.deptId());
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        SysUser directLeader = new SysUser();
        directLeader.setId(20L);
        directLeader.setRealName("直属领导");
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(dataScopeService.directLeaderId(employee.userId())).thenReturn(directLeader.getId());
        when(dataScopeService.requireUser(directLeader.getId())).thenReturn(directLeader);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of());
        when(monthPlanItemMapper.insert(any(BizMonthPlanItem.class))).thenAnswer(invocation -> {
            BizMonthPlanItem inserted = invocation.getArgument(0);
            inserted.setId(571L);
            return 1;
        });
        EmployeeController.SaveMonthPlanItemReq request = completeMonthPlanItemRequest(new BigDecimal("25"));
        plan.setPlanMonth(YearMonth.from(request.getDeadline()).toString());

        Map<String, Object> result = employeeService.submitExtraMonthPlanItem(employee, plan.getId(), request);

        assertThat(result.get("taskType")).isEqualTo("EXTRA");
        assertThat(result.get("status")).isEqualTo("submitted");
        ArgumentCaptor<BizMonthPlanItem> itemCaptor = ArgumentCaptor.forClass(BizMonthPlanItem.class);
        verify(monthPlanItemMapper).insert(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getPerformanceWeight()).isEqualByComparingTo("25");
        assertThat(itemCaptor.getValue().getStatus()).isEqualTo("PENDING");
        ArgumentCaptor<BizTodo> todoCaptor = ArgumentCaptor.forClass(BizTodo.class);
        verify(todoMapper).insert(todoCaptor.capture());
        assertThat(todoCaptor.getValue().getReceiverId()).isEqualTo(directLeader.getId());
        assertThat(todoCaptor.getValue().getObjectType()).isEqualTo("MONTH_PLAN_EXTRA_ITEM");
    }

    @Test
    void pendingExtraTaskCanBeWithdrawnEditedAndResubmitted() {
        EmployeeController.SaveMonthPlanItemReq request = completeMonthPlanItemRequest(new BigDecimal("15"));
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(59L);
        plan.setOwnerUserId(employee.userId());
        plan.setDeptId(employee.deptId());
        plan.setPlanMonth(YearMonth.from(request.getDeadline()).toString());
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(591L);
        item.setMonthPlanId(plan.getId());
        item.setTaskType("EXTRA");
        item.setStatus("PENDING");
        item.setVersionNo(1);
        item.setDeleted(0);
        BizTodo oldTodo = new BizTodo();
        oldTodo.setId(592L);
        oldTodo.setStatus("UNREAD");
        SysUser directLeader = new SysUser();
        directLeader.setId(20L);
        directLeader.setRealName("直属领导");
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(monthPlanItemMapper.selectById(item.getId())).thenReturn(item);
        when(todoMapper.selectList(any())).thenReturn(List.of(oldTodo));
        when(dataScopeService.directLeaderId(employee.userId())).thenReturn(directLeader.getId());
        when(dataScopeService.requireUser(directLeader.getId())).thenReturn(directLeader);

        employeeService.withdrawExtraMonthPlanItem(employee, plan.getId(), item.getId());
        employeeService.saveExtraMonthPlanItemDraft(employee, plan.getId(), item.getId(), request);
        Map<String, Object> result = employeeService.submitExtraMonthPlanItemDraft(employee, plan.getId(), item.getId());

        assertThat(oldTodo.getStatus()).isEqualTo("DONE");
        assertThat(result.get("status")).isEqualTo("submitted");
        assertThat(item.getStatus()).isEqualTo("PENDING");
        assertThat(item.getApproverId()).isEqualTo(directLeader.getId());
        assertThat(item.getTaskName()).isEqualTo(request.getTaskName());
        verify(todoMapper).insert(any(BizTodo.class));
    }

    @Test
    void extraTaskCannotBeSubmittedBeforeRegularMonthPlanApproval() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(58L);
        plan.setOwnerUserId(employee.userId());
        plan.setStatus("PENDING");
        plan.setDeleted(0);
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);

        BizException error = catchThrowableOfType(
                () -> employeeService.submitExtraMonthPlanItem(
                        employee, plan.getId(), completeMonthPlanItemRequest(new BigDecimal("10"))),
                BizException.class);

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("审批通过后");
        verify(monthPlanItemMapper, never()).insert(any(BizMonthPlanItem.class));
    }

    private BizMonthPlanItem completeMonthPlanItem(Long id, Long planId, BigDecimal weight) {
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(id);
        item.setMonthPlanId(planId);
        item.setTaskType("REGULAR");
        item.setPerformanceWeight(weight);
        item.setTaskName("常规任务");
        item.setTaskContent("完成常规任务");
        item.setTarget("按期完成");
        item.setDeliverable("交付成果");
        item.setAcceptanceStandard("验收通过");
        item.setEstimatedHours(new BigDecimal("8"));
        item.setDeadline(LocalDate.now().plusDays(5));
        item.setCompletionRate(0);
        item.setDeleted(0);
        return item;
    }

    private EmployeeController.SaveMonthPlanItemReq completeMonthPlanItemRequest(BigDecimal weight) {
        EmployeeController.SaveMonthPlanItemReq request = new EmployeeController.SaveMonthPlanItemReq();
        request.setTaskName("临时客户支持");
        request.setTaskContent("完成月初计划外的客户支持任务");
        request.setDeliverable("问题处理记录");
        request.setDeadline(LocalDate.now().plusDays(7));
        request.setPerformanceWeight(weight);
        return request;
    }

    @Test
    void pendingResultCannotBeSubmittedAgainForSamePlanItem() throws Exception {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(6L);
        plan.setOwnerUserId(employee.userId());
        plan.setDeptId(employee.deptId());
        plan.setTitle("月计划");
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        BizResult pending = new BizResult();
        pending.setId(61L);
        pending.setOwnerUserId(employee.userId());
        pending.setPlanId(plan.getId());
        pending.setStatus("PENDING");
        pending.setDeleted(0);
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(resultMapper.selectOne(any())).thenReturn(pending);
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        MockMultipartFile pdf = new MockMultipartFile("file", "evidence.pdf", "application/pdf", pdfBytes);

        BizException error = catchThrowableOfType(
                () -> employeeService.submitResult(employee, plan.getId(), null, 100, "重复提交", pdf),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("不能重复提交");
        verify(monthPlanMapper).selectForUpdateById(plan.getId());
        verify(resultMapper, never()).insert(any(BizResult.class));
        verify(resultEvidenceMapper, never()).insert(any(BizResultEvidence.class));
    }

    @Test
    void resultDetailReturnsOnlyLinkedPlanAndEvidenceData() {
        BizResult result = new BizResult();
        result.setId(62L);
        result.setOwnerUserId(employee.userId());
        result.setPlanType("MONTH");
        result.setPlanId(6L);
        result.setMonthPlanItemId(63L);
        result.setTitle("联调成果");
        result.setResultDate(LocalDate.of(2026, 7, 15));
        result.setContent("已完成联调");
        result.setCompletionRate(90);
        result.setVersionNo("V2");
        result.setStatus("PENDING");
        result.setIssueCodes("[]");
        result.setDeleted(0);
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(6L);
        plan.setOwnerUserId(employee.userId());
        plan.setTitle("七月联调计划");
        plan.setDeleted(0);
        BizMonthPlanItem planItem = new BizMonthPlanItem();
        planItem.setId(63L);
        planItem.setMonthPlanId(plan.getId());
        planItem.setTaskName("完成三端联调");
        planItem.setDeleted(0);
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(64L);
        evidence.setResultId(result.getId());
        evidence.setFileName("联调报告.pdf");
        evidence.setFileType("pdf");
        evidence.setFileSize(1024L);
        evidence.setChecksum("sha256");
        evidence.setDeleted(0);
        when(resultMapper.selectById(result.getId())).thenReturn(result);
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(monthPlanItemMapper.selectById(planItem.getId())).thenReturn(planItem);
        when(resultEvidenceMapper.selectList(any())).thenReturn(List.of(evidence));
        when(jsonCodec.stringList("[]")).thenReturn(List.of());

        Map<String, Object> detail = employeeService.resultDetail(employee, result.getId());

        assertThat(detail.get("planTitle")).isEqualTo("七月联调计划");
        assertThat(detail.get("planItemName")).isEqualTo("完成三端联调");
        assertThat((List<?>) detail.get("evidences")).hasSize(1);
        verify(accessService).requireOwner(employee, employee.userId());
    }

    @Test
    void resultDetailStopsBeforeEvidenceQueryWhenOwnershipFails() {
        AuthUser otherEmployee = new AuthUser(11L, "employee2", "其他员工", 110L, 110L,
                false, List.of("EMPLOYEE"), List.of());
        BizResult result = new BizResult();
        result.setId(65L);
        result.setOwnerUserId(employee.userId());
        result.setDeleted(0);
        when(resultMapper.selectById(result.getId())).thenReturn(result);
        doThrow(new BizException(403, "只能操作本人的数据"))
                .when(accessService).requireOwner(otherEmployee, employee.userId());

        BizException error = catchThrowableOfType(
                () -> employeeService.resultDetail(otherEmployee, result.getId()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(403);
        verifyNoInteractions(resultEvidenceMapper);
    }

    @Test
    void evidenceExtensionCannotDisguiseInvalidContent() {
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "not-a-pdf".getBytes()
        );

        BizException error = catchThrowableOfType(
                () -> employeeService.submitResult(employee, 1L, null, 100, "成果", fakePdf),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("文件类型不匹配");
        verifyNoInteractions(monthPlanMapper, resultMapper);
    }

    @Test
    void validPdfEvidenceCanBeSubmitted() throws Exception {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(1L);
        plan.setOwnerUserId(employee.userId());
        plan.setDeptId(employee.deptId());
        plan.setTitle("月计划");
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        SysUser leader = new SysUser();
        leader.setId(20L);
        leader.setRealName("直属领导");
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(resultMapper.selectCount(any())).thenReturn(0L);
        when(resultMapper.insert(any(BizResult.class))).thenAnswer(invocation -> {
            BizResult result = invocation.getArgument(0);
            result.setId(100L);
            return 1;
        });
        when(dataScopeService.directLeaderId(employee.userId())).thenReturn(leader.getId());
        when(dataScopeService.requireUser(leader.getId())).thenReturn(leader);
        byte[] pdfBytes;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdfBytes = output.toByteArray();
        }
        MockMultipartFile pdf = new MockMultipartFile("file", "evidence.pdf", "application/pdf", pdfBytes);

        var response = employeeService.submitResult(employee, plan.getId(), null, 100, "完成", pdf);

        assertThat(response.get("id")).isEqualTo(100L);
        assertThat(response.get("status")).isEqualTo("submitted");
        verify(resultEvidenceMapper).insert(any(BizResultEvidence.class));
        verify(todoMapper).insert(any(BizTodo.class));
    }

    @Test
    void evidenceDownloadCannotEscapeConfiguredUploadRoot() {
        BizResult result = new BizResult();
        result.setId(20L);
        result.setOwnerUserId(employee.userId());
        result.setDeleted(0);
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(21L);
        evidence.setResultId(result.getId());
        evidence.setFileName("outside.txt");
        evidence.setFileUrl("../../outside.txt");
        evidence.setDeleted(0);
        when(resultMapper.selectById(result.getId())).thenReturn(result);
        when(resultEvidenceMapper.selectById(evidence.getId())).thenReturn(evidence);

        BizException error = catchThrowableOfType(
                () -> employeeService.downloadEvidence(employee, result.getId(), evidence.getId()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(404);
        assertThat(error.getMessage()).contains("evidence file not found");
    }

    @Test
    void employeeEvidenceDownloadRejectsTamperedFile() throws Exception {
        BizResult result = new BizResult();
        result.setId(22L);
        result.setOwnerUserId(employee.userId());
        result.setDeleted(0);
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(23L);
        evidence.setResultId(result.getId());
        evidence.setFileName("evidence.pdf");
        evidence.setFileUrl("22/evidence.pdf");
        evidence.setChecksum("0000");
        evidence.setDeleted(0);
        Path file = uploadRoot.resolve(evidence.getFileUrl());
        Files.createDirectories(file.getParent());
        Files.writeString(file, "tampered evidence");
        when(resultMapper.selectById(result.getId())).thenReturn(result);
        when(resultEvidenceMapper.selectById(evidence.getId())).thenReturn(evidence);

        BizException error = catchThrowableOfType(
                () -> employeeService.downloadEvidence(employee, result.getId(), evidence.getId()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("完整性校验失败");
    }
}
