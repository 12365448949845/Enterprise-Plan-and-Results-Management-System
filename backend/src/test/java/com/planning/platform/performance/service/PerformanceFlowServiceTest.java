package com.planning.platform.performance.service;

import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.notification.service.UserMessageService;
import com.planning.platform.performance.domain.BizAcceptanceStandard;
import com.planning.platform.performance.domain.BizDeliverableTemplate;
import com.planning.platform.performance.domain.BizExportTask;
import com.planning.platform.performance.domain.BizPlanAdjustment;
import com.planning.platform.performance.domain.BizScoreRule;
import com.planning.platform.performance.domain.BizTodo;
import com.planning.platform.performance.dto.AcceptanceStandardSaveReqDTO;
import com.planning.platform.performance.dto.DeliverableTemplateSaveReqDTO;
import com.planning.platform.performance.dto.PerformanceActionReqDTO;
import com.planning.platform.performance.dto.ScoreRuleSaveReqDTO;
import com.planning.platform.performance.dto.ScoreRuleSimulateReqDTO;
import com.planning.platform.performance.mapper.BizAcceptanceStandardMapper;
import com.planning.platform.performance.mapper.BizDeliverableTemplateMapper;
import com.planning.platform.performance.mapper.BizExportTaskMapper;
import com.planning.platform.performance.mapper.BizPlanAdjustmentMapper;
import com.planning.platform.performance.mapper.BizScoreRuleMapper;
import com.planning.platform.performance.mapper.BizTodoMapper;
import com.planning.platform.performance.vo.PerformanceVO.OrgNodeVO;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerformanceFlowServiceTest {

    @Mock
    private PerformanceRoleGuard roleGuard;
    @Mock
    private PerformanceDataScopeService dataScopeService;
    @Mock
    private PerformanceJsonCodec jsonCodec;
    @Mock
    private LeaderPerformanceService leaderPerformanceDelegate;
    @Mock
    private ExportFileService exportFileService;
    @Mock
    private ExportTaskWorker exportTaskWorker;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private BizDayPlanMapper dayPlanMapper;
    @Mock
    private BizMonthPlanMapper monthPlanMapper;
    @Mock
    private BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock
    private BizResultMapper resultMapper;
    @Mock
    private BizResultEvidenceMapper resultEvidenceMapper;
    @Mock
    private BizEmployeeAppealMapper appealMapper;
    @Mock
    private BizPlanAdjustmentMapper planAdjustmentMapper;
    @Mock
    private BizTodoMapper todoMapper;
    @Mock
    private BizExportTaskMapper exportTaskMapper;
    @Mock
    private BizDeliverableTemplateMapper templateMapper;
    @Mock
    private BizAcceptanceStandardMapper acceptanceStandardMapper;
    @Mock
    private BizScoreRuleMapper scoreRuleMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserMessageService messageService;

    @InjectMocks
    private LeaderPerformanceService leaderService;

    @InjectMocks
    private DepartmentPerformanceService departmentService;

    private final AuthUser leader = new AuthUser(20L, "leader", "直属领导", 110L, 110L,
            false, List.of("DIRECT_LEADER"), List.of());
    private final AuthUser departmentOwner = new AuthUser(30L, "dept.owner", "部门负责人", 100L, null,
            false, List.of("DEPT_OWNER"), List.of());

    @BeforeEach
    void useExistingEntityStubsForLockingReads() {
        org.mockito.Mockito.lenient().when(dayPlanMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> dayPlanMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(monthPlanMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> monthPlanMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(monthPlanItemMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> monthPlanItemMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(resultMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> resultMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(planAdjustmentMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> planAdjustmentMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(todoMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> todoMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(appealMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> appealMapper.selectById((Long) invocation.getArgument(0)));
        org.mockito.Mockito.lenient().when(exportTaskMapper.selectForUpdateById(any()))
                .thenAnswer(invocation -> exportTaskMapper.selectById((String) invocation.getArgument(0)));
    }

    @Test
    void leaderOrgTreeOnlyReturnsAuthorizedNodes() {
        List<OrgNodeVO> authorizedTree = List.of(
                new OrgNodeVO(110L, "产品一组", "DEPARTMENT", List.of())
        );
        when(dataScopeService.orgTree(leader, false)).thenReturn(authorizedTree);

        var result = leaderService.orgTree(leader);

        assertThat(result).isEqualTo(authorizedTree);
        verify(roleGuard).requireLeaderModule(leader);
        verify(dataScopeService).orgTree(leader, false);
    }

    @Test
    void businessRecordIdsRejectMixedTextInsteadOfExtractingDigits() {
        BizException leaderError = catchThrowableOfType(
                () -> leaderService.dailyReviewDetail(leader, "DAY-1"),
                BizException.class
        );
        BizException departmentError = catchThrowableOfType(
                () -> departmentService.monthPlanApprovalDetail(departmentOwner, "PLAN-2"),
                BizException.class
        );

        assertThat(leaderError.getCode()).isEqualTo(400);
        assertThat(departmentError.getCode()).isEqualTo(400);
        assertThat(leaderError.getMessage()).contains("编号格式错误");
        assertThat(departmentError.getMessage()).contains("编号格式错误");
    }

    @Test
    void departmentOrgTreeOnlyReturnsAuthorizedNodes() {
        List<OrgNodeVO> authorizedTree = List.of(
                new OrgNodeVO(100L, "产品中心", "DEPARTMENT", List.of(
                        new OrgNodeVO(110L, "产品一组", "GROUP", List.of())
                ))
        );
        when(dataScopeService.orgTree(departmentOwner, true)).thenReturn(authorizedTree);

        var result = departmentService.orgTree(departmentOwner);

        assertThat(result).isEqualTo(authorizedTree);
        verify(roleGuard).requireDepartmentModule(departmentOwner);
        verify(dataScopeService).orgTree(departmentOwner, true);
    }

    @Test
    void departmentQuarterDashboardQueriesAllMonthsInQuarter() {
        SysUser owner = employee(10L);
        owner.setDeptId(110L);
        SysDept department = new SysDept();
        department.setId(110L);
        department.setName("产品一组");
        when(dataScopeService.departmentOwnerIds(departmentOwner, 110L)).thenReturn(Set.of(owner.getId()));
        when(dataScopeService.userMap()).thenReturn(Map.of(owner.getId(), owner));
        when(dataScopeService.departmentMap()).thenReturn(Map.of(department.getId(), department));
        when(monthPlanMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectList(any())).thenReturn(List.of());
        when(todoMapper.selectList(any())).thenReturn(List.of());
        when(exportTaskMapper.selectList(any())).thenReturn(List.of());
        when(dayPlanMapper.selectCount(any())).thenReturn(0L);

        var dashboard = departmentService.dashboard(departmentOwner, 110L, "QUARTER", "2026-05");

        verify(monthPlanMapper).selectList(any());
        Object period = ReflectionTestUtils.invokeMethod(
                departmentService, "dashboardPeriod", "QUARTER", YearMonth.of(2026, 5));
        assertThat(String.valueOf(period)).contains("2026-04", "2026-06");
        assertThat(dashboard.summaries()).hasSize(1);
    }

    @Test
    void departmentPeriodInputsRejectInvalidMonths() {
        BizException dashboardError = catchThrowableOfType(
                () -> departmentService.dashboard(departmentOwner, 100L, "MONTH", "2026-13"),
                BizException.class
        );
        BizException resultError = catchThrowableOfType(
                () -> departmentService.resultConfirms(departmentOwner, 100L, "2026-00", null, null),
                BizException.class
        );
        BizException approvalError = catchThrowableOfType(
                () -> departmentService.monthPlanApprovals(departmentOwner, 2026, 13, 100L, null, null),
                BizException.class
        );

        assertThat(dashboardError.getCode()).isEqualTo(422);
        assertThat(resultError.getCode()).isEqualTo(422);
        assertThat(approvalError.getCode()).isEqualTo(422);
    }

    @Test
    void leaderWorkbenchAppliesSelectedMonthToReviewsAndSuggestions() {
        when(dataScopeService.leaderOwnerIds(leader, 110L)).thenReturn(Set.of(10L));
        when(dataScopeService.orgTree(leader, false)).thenReturn(List.of());
        when(dayPlanMapper.selectList(any())).thenReturn(List.of());
        when(resultMapper.selectList(any())).thenReturn(List.of());

        leaderService.workbench(leader, 110L, null, "2026-05");

        Object period = ReflectionTestUtils.invokeMethod(
                leaderService, "workbenchPeriod", null, "2026-05");
        assertThat(String.valueOf(period)).contains("2026-05-01", "2026-05-31");
        verify(dayPlanMapper).selectList(any());
        verify(resultMapper).selectList(any());
    }

    @Test
    void dailyReviewCompletenessFilterDistinguishesCompleteAndMissingRows() {
        BizDayPlan completePlan = new BizDayPlan();
        completePlan.setId(1L);
        completePlan.setOwnerUserId(10L);
        completePlan.setMonthPlanItemId(11L);
        completePlan.setPlanDate(LocalDate.of(2026, 7, 15));
        completePlan.setContent("完成接口联调");
        completePlan.setStatus("PENDING");
        completePlan.setDeleted(0);
        BizDayPlan missingPlan = new BizDayPlan();
        missingPlan.setId(2L);
        missingPlan.setOwnerUserId(10L);
        missingPlan.setMonthPlanItemId(12L);
        missingPlan.setPlanDate(LocalDate.of(2026, 7, 15));
        missingPlan.setContent("补充测试记录");
        missingPlan.setStatus("PENDING");
        missingPlan.setDeleted(0);
        BizMonthPlanItem completeItem = completeMonthItem(11L);
        BizMonthPlanItem missingItem = new BizMonthPlanItem();
        missingItem.setId(12L);
        missingItem.setDeleted(0);
        when(dataScopeService.leaderOwnerIds(leader, 110L)).thenReturn(Set.of(10L));
        when(dataScopeService.userMap()).thenReturn(Map.of(10L, employee(10L)));
        when(dayPlanMapper.selectList(any())).thenReturn(List.of(completePlan, missingPlan));
        when(monthPlanItemMapper.selectBatchIds(any())).thenReturn(List.of(completeItem, missingItem));

        var completeRows = leaderService.dailyReviews(leader, 110L, null, null, null, false);
        var missingRows = leaderService.dailyReviews(leader, 110L, null, null, null, true);

        assertThat(completeRows).extracting(item -> item.id()).containsExactly("1");
        assertThat(missingRows).extracting(item -> item.id()).containsExactly("2");
    }

    @Test
    void leaderDailyReviewRejectsBlankComment() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(3L);
        plan.setOwnerUserId(10L);
        plan.setStatus("PENDING");
        plan.setReviewStatus("PENDING_COMMENT");
        plan.setDeleted(0);
        when(dayPlanMapper.selectById(plan.getId())).thenReturn(plan);
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("   ");

        BizException error = catchThrowableOfType(
                () -> leaderService.commentDailyPlan(leader, "3", request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("点评内容不能为空");
        verify(dayPlanMapper, never()).updateById(plan);
    }

    @Test
    void leaderDailyReviewRejectsCommentLongerThanDatabaseColumn() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(4L);
        plan.setOwnerUserId(10L);
        plan.setStatus("PENDING");
        plan.setReviewStatus("PENDING_COMMENT");
        plan.setDeleted(0);
        when(dayPlanMapper.selectById(plan.getId())).thenReturn(plan);
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("a".repeat(501));

        BizException error = catchThrowableOfType(
                () -> leaderService.commentDailyPlan(leader, "4", request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("500");
        verify(dayPlanMapper, never()).updateById(plan);
    }

    @Test
    void leaderDailyReviewRejectsUnsupportedRiskLevel() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(5L);
        plan.setOwnerUserId(10L);
        plan.setStatus("PENDING");
        plan.setReviewStatus("PENDING_COMMENT");
        plan.setRiskLevel("LOW");
        plan.setDeleted(0);
        when(dayPlanMapper.selectById(plan.getId())).thenReturn(plan);
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("按计划推进");
        request.setRiskLevel("CRITICAL");

        BizException error = catchThrowableOfType(
                () -> leaderService.commentDailyPlan(leader, "5", request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("LOW、MEDIUM、HIGH");
        verify(dayPlanMapper, never()).updateById(plan);
    }

    @Test
    void planAdjustmentStoresOperationCommentAndAlwaysKeepsEvidenceChain() {
        BizPlanAdjustment adjustment = new BizPlanAdjustment();
        adjustment.setId(91L);
        adjustment.setOwnerUserId(10L);
        adjustment.setStatus("PENDING");
        adjustment.setDeleted(0);
        when(planAdjustmentMapper.selectById(adjustment.getId())).thenReturn(adjustment);
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setAction("PAUSE");
        request.setComment("  同意暂停，等待外部依赖恢复。  ");
        request.setKeepEvidenceChain(false);

        var result = leaderService.processPlanAdjustment(leader, "91", request);

        assertThat(result.status()).isEqualTo("PAUSE");
        assertThat(adjustment.getStatus()).isEqualTo("PAUSED");
        assertThat(adjustment.getOperationComment()).isEqualTo("同意暂停，等待外部依赖恢复。");
        assertThat(adjustment.getKeepEvidenceChain()).isTrue();
        verify(planAdjustmentMapper).updateById(adjustment);
    }

    @Test
    void processedPlanAdjustmentCannotBeChangedAgain() {
        BizPlanAdjustment adjustment = new BizPlanAdjustment();
        adjustment.setId(90L);
        adjustment.setOwnerUserId(10L);
        adjustment.setStatus("PAUSED");
        adjustment.setDeleted(0);
        when(planAdjustmentMapper.selectById(adjustment.getId())).thenReturn(adjustment);
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setAction("CANCEL");

        BizException error = catchThrowableOfType(
                () -> leaderService.processPlanAdjustment(leader, "90", request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        verify(roleGuard).requireLeaderModule(leader);
        verify(dataScopeService).requireLeaderOwner(leader, adjustment.getOwnerUserId());
        verify(planAdjustmentMapper, never()).updateById(adjustment);
    }

    @Test
    void scoreSimulationUsesSavedFactorWeights() {
        BizScoreRule rule = new BizScoreRule();
        rule.setId(70L);
        rule.setDeptId(110L);
        rule.setRuleJson("rule-json");
        rule.setDeleted(0);
        when(scoreRuleMapper.selectById(rule.getId())).thenReturn(rule);
        when(dataScopeService.departmentScope(departmentOwner.deptId())).thenReturn(Set.of(100L, 110L));
        when(jsonCodec.objectMap("rule-json")).thenReturn(Map.of("factors", List.of(
                Map.of("code", "completion_ratio", "weight", 70, "enabled", true),
                Map.of("code", "overdue_count", "weight", 10, "penaltyPerTime", 2, "enabled", true),
                Map.of("code", "reject_count", "weight", 10, "penaltyPerTime", 3, "enabled", true),
                Map.of("code", "review_passed", "weight", 10, "enabled", true)
        )));
        ScoreRuleSimulateReqDTO request = new ScoreRuleSimulateReqDTO();
        request.setEmployeeName("测试员工");
        request.setCompletionRatio(new BigDecimal("80"));
        request.setOverdueCount(1);
        request.setRejectCount(0);
        request.setReviewPassed(true);

        var simulation = departmentService.simulateScoreRule(departmentOwner, rule.getId(), request);

        assertThat(simulation.score()).isEqualByComparingTo("84.00");
        assertThat(simulation.hitFactors()).containsExactly(
                "completion_ratio", "overdue_count", "reject_count", "review_passed");
    }

    @Test
    void deliverableTemplateSavePreservesCanonicalFieldCodes() {
        when(dataScopeService.departmentScope(departmentOwner.deptId())).thenReturn(Set.of(100L, 110L));
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");
        DeliverableTemplateSaveReqDTO request = new DeliverableTemplateSaveReqDTO();
        request.setOrgId(110L);
        request.setTemplateName("  数据验收表  ");
        request.setEvidenceType("spreadsheet");
        request.setRequired(true);
        request.setAppliesTo("MONTH_PLAN, RESULT,MONTH_PLAN");
        request.setDescription("  用于月度验收  ");

        departmentService.saveTemplate(departmentOwner, null, request);

        ArgumentCaptor<BizDeliverableTemplate> captor = ArgumentCaptor.forClass(BizDeliverableTemplate.class);
        verify(templateMapper).insert(captor.capture());
        BizDeliverableTemplate saved = captor.getValue();
        assertThat(saved.getTemplateName()).isEqualTo("数据验收表");
        assertThat(saved.getEvidenceType()).isEqualTo("SPREADSHEET");
        assertThat(saved.getAppliesTo()).isEqualTo("MONTH_PLAN,RESULT");
        assertThat(saved.getDescription()).isEqualTo("用于月度验收");
    }

    @Test
    void templateUpdateRejectsRecordFromOutsideOriginalScope() {
        BizDeliverableTemplate existing = new BizDeliverableTemplate();
        existing.setId(71L);
        existing.setDeptId(999L);
        existing.setDeleted(0);
        when(templateMapper.selectById(existing.getId())).thenReturn(existing);
        when(dataScopeService.departmentScope(departmentOwner.deptId())).thenReturn(Set.of(100L, 110L));
        DeliverableTemplateSaveReqDTO request = new DeliverableTemplateSaveReqDTO();
        request.setOrgId(100L);
        request.setTemplateName("越权迁移模板");
        request.setEvidenceType("DOCUMENT");
        request.setRequired(true);
        request.setAppliesTo("RESULT");

        BizException error = catchThrowableOfType(
                () -> departmentService.saveTemplate(departmentOwner, existing.getId(), request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(403);
        verify(templateMapper, never()).updateById(any(BizDeliverableTemplate.class));
    }

    @Test
    void acceptanceStandardUpdateRejectsRecordFromOutsideOriginalScope() {
        BizDeliverableTemplate outsideTemplate = new BizDeliverableTemplate();
        outsideTemplate.setId(81L);
        outsideTemplate.setDeptId(999L);
        outsideTemplate.setDeleted(0);
        BizDeliverableTemplate allowedTemplate = new BizDeliverableTemplate();
        allowedTemplate.setId(82L);
        allowedTemplate.setDeptId(110L);
        allowedTemplate.setDeleted(0);
        BizAcceptanceStandard existing = new BizAcceptanceStandard();
        existing.setId(83L);
        existing.setTemplateId(outsideTemplate.getId());
        existing.setDeleted(0);
        when(acceptanceStandardMapper.selectById(existing.getId())).thenReturn(existing);
        when(templateMapper.selectById(allowedTemplate.getId())).thenReturn(allowedTemplate);
        when(templateMapper.selectById(outsideTemplate.getId())).thenReturn(outsideTemplate);
        when(dataScopeService.departmentScope(departmentOwner.deptId())).thenReturn(Set.of(100L, 110L));
        AcceptanceStandardSaveReqDTO request = new AcceptanceStandardSaveReqDTO();
        request.setTemplateId(allowedTemplate.getId());
        request.setStandardText("试图迁移到可见模板");

        BizException error = catchThrowableOfType(
                () -> departmentService.saveAcceptanceStandard(departmentOwner, existing.getId(), request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(403);
        verify(acceptanceStandardMapper, never()).updateById(any(BizAcceptanceStandard.class));
    }

    @Test
    void scoreRuleUpdateRejectsRecordFromOutsideOriginalScope() {
        BizScoreRule existing = new BizScoreRule();
        existing.setId(91L);
        existing.setDeptId(999L);
        existing.setDeleted(0);
        when(scoreRuleMapper.selectById(existing.getId())).thenReturn(existing);
        when(dataScopeService.departmentScope(departmentOwner.deptId())).thenReturn(Set.of(100L, 110L));
        ScoreRuleSaveReqDTO request = new ScoreRuleSaveReqDTO();
        request.setOrgId(100L);
        request.setRuleName("越权迁移规则");
        request.setRuleJson(Map.of());

        BizException error = catchThrowableOfType(
                () -> departmentService.saveScoreRule(departmentOwner, existing.getId(), request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(403);
        verify(scoreRuleMapper, never()).updateById(any(BizScoreRule.class));
    }

    @Test
    void enablingFutureScoreRuleClosesCurrentRuleWithoutLosingHistory() {
        BizScoreRule current = new BizScoreRule();
        current.setId(92L);
        current.setDeptId(110L);
        current.setStatus("ENABLED");
        current.setEffectiveStart(LocalDate.of(2026, 7, 1));
        current.setDeleted(0);
        BizScoreRule future = new BizScoreRule();
        future.setId(93L);
        future.setDeptId(110L);
        future.setStatus("DRAFT");
        future.setEffectiveStart(LocalDate.of(2026, 8, 1));
        future.setDeleted(0);
        when(scoreRuleMapper.selectById(future.getId())).thenReturn(future);
        when(scoreRuleMapper.selectList(any())).thenReturn(List.of(current));
        when(dataScopeService.departmentScope(departmentOwner.deptId())).thenReturn(Set.of(100L, 110L));

        var result = departmentService.enableScoreRule(departmentOwner, future.getId());

        assertThat(result.status()).isEqualTo("ENABLED");
        assertThat(current.getStatus()).isEqualTo("ENABLED");
        assertThat(current.getEffectiveEnd()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(future.getStatus()).isEqualTo("ENABLED");
        verify(scoreRuleMapper).updateById(current);
        verify(scoreRuleMapper).updateById(future);
    }

    @Test
    void departmentLedgerUsesDepartmentOwnerScopeDelegate() {
        when(leaderPerformanceDelegate.departmentLedgers(
                departmentOwner, 110L, "MONTH", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "员工"
        )).thenReturn(List.of());

        var result = departmentService.departmentLedgers(
                departmentOwner, 110L, "MONTH", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "员工"
        );

        assertThat(result).isEmpty();
        verify(leaderPerformanceDelegate).departmentLedgers(
                departmentOwner, 110L, "MONTH", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "员工"
        );
        verify(leaderPerformanceDelegate, never()).teamLedgers(any(), any(), any(), any(), any(), any());
    }

    @Test
    void teamLedgerUsesInheritedEnabledScoreRule() {
        SysUser owner = employee(10L);
        owner.setDeptId(110L);
        SysDept group = new SysDept();
        group.setId(110L);
        group.setParentId(100L);
        SysDept department = new SysDept();
        department.setId(100L);
        department.setParentId(1L);
        BizScoreRule rule = new BizScoreRule();
        rule.setId(101L);
        rule.setDeptId(100L);
        rule.setRuleJson("score-rule-json");
        rule.setStatus("ENABLED");
        rule.setDeleted(0);
        BizResult result = new BizResult();
        result.setId(102L);
        result.setOwnerUserId(owner.getId());
        result.setCompletionRate(80);
        result.setEvidenceStatus("COMPLETE");
        result.setStatus("CONFIRMED");
        result.setDeleted(0);
        when(dataScopeService.leaderOwnerIds(leader, 110L)).thenReturn(Set.of(owner.getId()));
        when(dataScopeService.userMap()).thenReturn(Map.of(owner.getId(), owner));
        when(dataScopeService.departmentMap()).thenReturn(Map.of(110L, group, 100L, department));
        when(scoreRuleMapper.selectList(any())).thenReturn(List.of(rule));
        when(monthPlanMapper.selectCount(any())).thenReturn(1L);
        when(resultMapper.selectList(any())).thenReturn(List.of(result));
        when(dayPlanMapper.selectCount(any())).thenReturn(1L);
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(jsonCodec.objectMap("score-rule-json")).thenReturn(Map.of("factors", List.of(
                Map.of("code", "completion_ratio", "weight", 70, "enabled", true),
                Map.of("code", "overdue_count", "weight", 10, "penaltyPerTime", 2, "enabled", true),
                Map.of("code", "reject_count", "weight", 10, "penaltyPerTime", 3, "enabled", true),
                Map.of("code", "review_passed", "weight", 10, "enabled", true)
        )));

        var ledgers = leaderService.teamLedgers(
                leader, 110L, "MONTH", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), null);

        assertThat(ledgers).hasSize(1);
        assertThat(ledgers.get(0).referenceScore()).isEqualByComparingTo("84.00");
    }

    @Test
    void leaderCommentUpdatesDayPlanAndEmployeeVisibleStatus() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(1L);
        plan.setOwnerUserId(10L);
        plan.setStatus("PENDING");
        plan.setDeleted(0);
        when(dayPlanMapper.selectById(1L)).thenReturn(plan);
        when(todoMapper.selectList(any())).thenReturn(List.of());

        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("按节点推进");
        request.setRiskLevel("LOW");

        var result = leaderService.commentDailyPlan(leader, "1", request);

        assertThat(result.status()).isEqualTo("COMMENTED");
        assertThat(plan.getStatus()).isEqualTo("APPROVED");
        assertThat(plan.getReviewStatus()).isEqualTo("COMMENTED");
        assertThat(plan.getApprovalComment()).isEqualTo("按节点推进");
        assertThat(plan.getReviewedBy()).isEqualTo(leader.userId());
        verify(dayPlanMapper).selectForUpdateById(plan.getId());
        verify(dayPlanMapper).updateById(plan);
    }

    @Test
    void leaderRiskCreatesDepartmentOwnerReviewTodoAndClosesLeaderTodo() {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(7L);
        plan.setOwnerUserId(10L);
        plan.setDeptId(110L);
        plan.setStatus("PENDING");
        plan.setReviewStatus("PENDING_COMMENT");
        plan.setDeleted(0);
        SysUser receiver = new SysUser();
        receiver.setId(departmentOwner.userId());
        receiver.setRealName("部门负责人");
        BizTodo leaderTodo = new BizTodo();
        leaderTodo.setId(70L);
        leaderTodo.setStatus("UNREAD");
        when(dayPlanMapper.selectById(7L)).thenReturn(plan);
        when(todoMapper.selectList(any())).thenReturn(List.of(leaderTodo));
        when(dataScopeService.departmentOwnerId(110L)).thenReturn(departmentOwner.userId());
        when(dataScopeService.requireUser(departmentOwner.userId())).thenReturn(receiver);

        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setRiskLevel("HIGH");
        request.setComment("存在客户验收阻断风险");

        var result = leaderService.markDailyRisk(leader, "7", request);

        assertThat(result.status()).isEqualTo("RISK_MARKED");
        assertThat(plan.getReviewStatus()).isEqualTo("RISK_MARKED");
        assertThat(leaderTodo.getStatus()).isEqualTo("DONE");
        ArgumentCaptor<BizTodo> todoCaptor = ArgumentCaptor.forClass(BizTodo.class);
        verify(todoMapper).insert(todoCaptor.capture());
        assertThat(todoCaptor.getValue().getReceiverId()).isEqualTo(departmentOwner.userId());
        assertThat(todoCaptor.getValue().getTitle()).isEqualTo("日计划补审");
        assertThat(todoCaptor.getValue().getRouteHint()).isEqualTo("/department/todo");
    }

    @Test
    void departmentApprovesRiskDayPlanAndCompletesTodo() {
        BizDayPlan plan = riskDayPlan(8L);
        BizMonthPlanItem item = completeMonthItem(81L);
        plan.setMonthPlanItemId(item.getId());
        SysUser owner = employee(10L);
        BizTodo todo = new BizTodo();
        todo.setId(80L);
        todo.setStatus("READ");
        when(dayPlanMapper.selectById(8L)).thenReturn(plan);
        when(monthPlanItemMapper.selectById(item.getId())).thenReturn(item);
        when(dataScopeService.requireUser(owner.getId())).thenReturn(owner);
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");
        when(todoMapper.selectList(any())).thenReturn(List.of(todo));

        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("风险措施明确，同意继续执行");

        var result = departmentService.approveDayPlanReview(departmentOwner, "8", request);

        assertThat(result.status()).isEqualTo("RISK_RESOLVED");
        assertThat(plan.getStatus()).isEqualTo("APPROVED");
        assertThat(plan.getReviewStatus()).isEqualTo("RISK_RESOLVED");
        assertThat(plan.getDepartmentReviewComment()).isEqualTo("风险措施明确，同意继续执行");
        assertThat(plan.getApproverId()).isEqualTo(departmentOwner.userId());
        assertThat(todo.getStatus()).isEqualTo("DONE");
        verify(dayPlanMapper).updateById(plan);
    }

    @Test
    void departmentRejectsRiskDayPlanForEmployeeSupplement() {
        BizDayPlan plan = riskDayPlan(9L);
        when(dayPlanMapper.selectById(9L)).thenReturn(plan);
        when(todoMapper.selectList(any())).thenReturn(List.of());

        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("请补充风险应对措施和验收节点");

        var result = departmentService.rejectDayPlanReview(departmentOwner, "9", request);

        assertThat(result.status()).isEqualTo("SUPPLEMENT_REQUIRED");
        assertThat(plan.getStatus()).isEqualTo("REJECTED");
        assertThat(plan.getReviewStatus()).isEqualTo("SUPPLEMENT_REQUIRED");
        assertThat(plan.getDepartmentReviewComment()).contains("风险应对措施");
        verify(dayPlanMapper).updateById(plan);
    }

    @Test
    void directLeaderApprovalUpdatesPlanAndAllItems() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(2L);
        plan.setOwnerUserId(10L);
        plan.setDeptId(110L);
        plan.setPlanMonth("2026-07");
        plan.setContent("月计划");
        plan.setStatus("PENDING");
        plan.setVersionNo(1);
        plan.setDeleted(0);
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(21L);
        item.setMonthPlanId(2L);
        item.setDeliverable("成果报告");
        item.setAcceptanceStandard("评审通过");
        item.setEstimatedHours(new BigDecimal("16"));
        item.setDeadline(LocalDate.of(2026, 7, 31));
        item.setDeleted(0);
        SysUser owner = new SysUser();
        owner.setId(10L);
        owner.setEmployeeNo("E001");
        owner.setRealName("员工");
        when(monthPlanMapper.selectById(2L)).thenReturn(plan);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(item));
        when(dataScopeService.requireUser(10L)).thenReturn(owner);
        when(dataScopeService.directLeaderId(10L)).thenReturn(leader.userId());
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");
        when(todoMapper.selectList(any())).thenReturn(List.of());

        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("同意执行");

        var result = departmentService.approveMonthPlan(leader, "2", request);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(plan.getStatus()).isEqualTo("APPROVED");
        assertThat(plan.getApprovalComment()).isEqualTo("同意执行");
        assertThat(item.getStatus()).isEqualTo("APPROVED");
        verify(monthPlanMapper).selectForUpdateById(plan.getId());
        verify(monthPlanMapper).updateById(plan);
        verify(monthPlanItemMapper).updateById(item);
    }

    @Test
    void monthPlanApprovalDetailReturnsStructuredPlanItems() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(23L);
        plan.setOwnerUserId(10L);
        plan.setDeptId(110L);
        plan.setPlanMonth("2026-07");
        plan.setContent("月度重点工作");
        plan.setStatus("PENDING");
        plan.setVersionNo(1);
        plan.setDeleted(0);
        BizMonthPlanItem first = completeMonthItem(231L);
        first.setMonthPlanId(plan.getId());
        first.setTaskName("完成接口联调");
        first.setTaskContent("联调员工端和领导端");
        first.setTarget("流程闭环");
        first.setCompletionRate(20);
        BizMonthPlanItem second = completeMonthItem(232L);
        second.setMonthPlanId(plan.getId());
        second.setTaskName("完成验收测试");
        second.setTaskContent("执行关键场景验收");
        second.setTarget("测试通过");
        second.setCompletionRate(0);
        SysUser owner = employee(10L);
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(dataScopeService.requireUser(owner.getId())).thenReturn(owner);
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");

        var detail = departmentService.monthPlanApprovalDetail(departmentOwner, String.valueOf(plan.getId()));

        assertThat(detail.items()).hasSize(2);
        assertThat(detail.items()).extracting(item -> item.taskName())
                .containsExactly("完成接口联调", "完成验收测试");
        assertThat(detail.items().get(0).deliverable()).isEqualTo("部署检查清单");
    }

    @Test
    void monthPlanApprovalPageReturnsDatabasePageMetadata() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(24L);
        plan.setOwnerUserId(10L);
        plan.setDeptId(110L);
        plan.setPlanMonth("2026-07");
        plan.setContent("分页审批计划");
        plan.setStatus("PENDING");
        plan.setVersionNo(1);
        plan.setDeleted(0);
        BizMonthPlanItem item = completeMonthItem(241L);
        item.setMonthPlanId(plan.getId());
        item.setTaskName("分页验证事项");
        item.setTaskContent("验证数据库分页元数据");
        item.setTarget("返回正确总数");
        item.setCompletionRate(0);
        SysUser owner = employee(10L);
        owner.setDeptId(110L);
        when(dataScopeService.departmentOwnerIds(departmentOwner, 110L)).thenReturn(Set.of(owner.getId()));
        when(dataScopeService.userMap()).thenReturn(Map.of(owner.getId(), owner));
        when(monthPlanMapper.selectCount(any())).thenReturn(12L);
        when(monthPlanMapper.selectList(any())).thenReturn(List.of(plan));
        when(monthPlanItemMapper.selectList(any())).thenReturn(List.of(item));
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");

        var page = departmentService.monthPlanApprovalsPage(
                departmentOwner, 2026, 7, 110L, "PENDING_APPROVAL", null, 2, 10);

        assertThat(page.total()).isEqualTo(12);
        assertThat(page.pageNo()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(10);
        assertThat(page.items()).extracting(value -> value.id()).containsExactly("24");
    }

    @Test
    void monthPlanApprovalPageRejectsOversizedPage() {
        BizException error = catchThrowableOfType(
                () -> departmentService.monthPlanApprovalsPage(
                        departmentOwner, 2026, 7, 110L, null, null, 1, 101),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("每页数量");
    }

    @Test
    void directLeaderCannotOverwriteFinalMonthPlan() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(22L);
        plan.setOwnerUserId(10L);
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        when(monthPlanMapper.selectById(22L)).thenReturn(plan);
        when(dataScopeService.directLeaderId(10L)).thenReturn(leader.userId());

        BizException error = catchThrowableOfType(
                () -> departmentService.rejectMonthPlan(leader, "22", new PerformanceActionReqDTO()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("不能重复处理");
        assertThat(plan.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void departmentOwnerCannotApprovePendingMonthPlan() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(26L);
        plan.setOwnerUserId(10L);
        plan.setStatus("PENDING");
        plan.setDeleted(0);
        when(monthPlanMapper.selectById(26L)).thenReturn(plan);
        when(dataScopeService.directLeaderId(10L)).thenReturn(leader.userId());

        BizException error = catchThrowableOfType(
                () -> departmentService.approveMonthPlan(departmentOwner, "26", new PerformanceActionReqDTO()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(403);
        assertThat(error.getMessage()).contains("直属领导");
    }

    @Test
    void assignedDirectLeaderApprovesOnlyTheExtraTask() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(25L);
        plan.setOwnerUserId(10L);
        plan.setDeptId(110L);
        plan.setStatus("APPROVED");
        plan.setDeleted(0);
        BizMonthPlanItem item = completeMonthItem(251L);
        item.setMonthPlanId(plan.getId());
        item.setTaskType("EXTRA");
        item.setPerformanceWeight(new BigDecimal("20"));
        item.setTaskName("临时专项任务");
        item.setStatus("PENDING");
        when(monthPlanItemMapper.selectById(item.getId())).thenReturn(item);
        when(monthPlanMapper.selectById(plan.getId())).thenReturn(plan);
        when(dataScopeService.directLeaderId(plan.getOwnerUserId())).thenReturn(leader.userId());
        when(todoMapper.selectList(any())).thenReturn(List.of());
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setComment("同意纳入额外任务");

        var result = leaderService.approveExtraMonthPlanItem(leader, String.valueOf(item.getId()), request);

        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(item.getStatus()).isEqualTo("APPROVED");
        assertThat(item.getApproverId()).isEqualTo(leader.userId());
        assertThat(plan.getStatus()).isEqualTo("APPROVED");
        verify(monthPlanItemMapper).updateById(item);
        verify(monthPlanMapper, never()).updateById(plan);
        verify(auditLogService).success(leader, "EXTRA_MONTH_PLAN_ITEM_APPROVE", "MONTH_PLAN_ITEM", item.getId(),
                "{\"status\":\"APPROVED\",\"monthPlanId\":25}");
    }

    @Test
    void departmentStrongAuthenticationConfirmsResultAndEvidence() {
        BizResult resultRow = new BizResult();
        resultRow.setId(3L);
        resultRow.setOwnerUserId(10L);
        resultRow.setDeptId(110L);
        resultRow.setTitle("成果");
        resultRow.setStatus("PENDING");
        resultRow.setEvidenceStatus("COMPLETE");
        resultRow.setSuggestionStatus("SUGGEST_CONFIRM");
        resultRow.setIssueCodes("[]");
        resultRow.setCompletionRate(100);
        resultRow.setDeleted(0);
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(31L);
        evidence.setResultId(3L);
        evidence.setFileName("result.pdf");
        evidence.setFileType("pdf");
        evidence.setStatus("UPLOADED");
        evidence.setReviewPassed(false);
        evidence.setDeleted(0);
        SysUser owner = new SysUser();
        owner.setId(10L);
        owner.setEmployeeNo("E001");
        owner.setRealName("员工");
        SysUser confirmer = new SysUser();
        confirmer.setId(departmentOwner.userId());
        confirmer.setPasswordHash("encoded-password");
        when(resultMapper.selectById(3L)).thenReturn(resultRow);
        when(resultEvidenceMapper.selectList(any())).thenReturn(List.of(evidence));
        when(dataScopeService.requireUser(10L)).thenReturn(owner);
        when(dataScopeService.requireUser(departmentOwner.userId())).thenReturn(confirmer);
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");
        when(jsonCodec.stringList("[]")).thenReturn(List.of());
        when(passwordEncoder.matches("Demo@123456", "encoded-password")).thenReturn(true);
        when(todoMapper.selectList(any())).thenReturn(List.of());

        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setAuthPassword("Demo@123456");
        request.setComment("验收通过");

        var action = departmentService.confirmResult(departmentOwner, "3", request);

        assertThat(action.status()).isEqualTo("CONFIRMED");
        assertThat(resultRow.getStatus()).isEqualTo("CONFIRMED");
        assertThat(resultRow.getVerifyRecordId()).startsWith("PASSWORD_REAUTH:");
        assertThat(evidence.getReviewPassed()).isTrue();
        assertThat(evidence.getStatus()).isEqualTo("REVIEW_PASSED");
        verify(resultMapper).updateById(resultRow);
        verify(resultEvidenceMapper).updateById(evidence);
    }

    private BizDayPlan riskDayPlan(Long id) {
        BizDayPlan plan = new BizDayPlan();
        plan.setId(id);
        plan.setOwnerUserId(10L);
        plan.setDeptId(110L);
        plan.setPlanDate(LocalDate.of(2026, 7, 14));
        plan.setContent("完成客户环境部署检查");
        plan.setStatus("PENDING");
        plan.setReviewStatus("RISK_MARKED");
        plan.setRiskLevel("HIGH");
        plan.setApprovalComment("客户环境存在阻断风险");
        plan.setDeleted(0);
        return plan;
    }

    private BizMonthPlanItem completeMonthItem(Long id) {
        BizMonthPlanItem item = new BizMonthPlanItem();
        item.setId(id);
        item.setDeliverable("部署检查清单");
        item.setAcceptanceStandard("阻断问题全部登记并明确责任人");
        item.setEstimatedHours(new BigDecimal("8"));
        item.setDeleted(0);
        return item;
    }

    private SysUser employee(Long id) {
        SysUser owner = new SysUser();
        owner.setId(id);
        owner.setEmployeeNo("E001");
        owner.setRealName("员工");
        return owner;
    }

    @Test
    void departmentStrongAuthenticationRejectsWrongPassword() {
        BizResult resultRow = new BizResult();
        resultRow.setId(4L);
        resultRow.setOwnerUserId(10L);
        resultRow.setDeptId(110L);
        resultRow.setTitle("成果");
        resultRow.setStatus("PENDING");
        resultRow.setEvidenceStatus("COMPLETE");
        resultRow.setSuggestionStatus("SUGGEST_CONFIRM");
        resultRow.setIssueCodes("[]");
        resultRow.setCompletionRate(100);
        resultRow.setDeleted(0);
        BizResultEvidence evidence = new BizResultEvidence();
        evidence.setId(41L);
        evidence.setResultId(4L);
        evidence.setFileName("result.pdf");
        evidence.setStatus("UPLOADED");
        evidence.setDeleted(0);
        SysUser owner = new SysUser();
        owner.setId(10L);
        owner.setRealName("员工");
        SysUser confirmer = new SysUser();
        confirmer.setId(departmentOwner.userId());
        confirmer.setPasswordHash("encoded-password");
        when(resultMapper.selectById(4L)).thenReturn(resultRow);
        when(resultEvidenceMapper.selectList(any())).thenReturn(List.of(evidence));
        when(dataScopeService.requireUser(10L)).thenReturn(owner);
        when(dataScopeService.requireUser(departmentOwner.userId())).thenReturn(confirmer);
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");
        when(jsonCodec.stringList("[]")).thenReturn(List.of());
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setAuthPassword("wrong-password");

        BizException error = catchThrowableOfType(
                () -> departmentService.confirmResult(departmentOwner, "4", request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(422);
        assertThat(error.getMessage()).contains("密码不正确");
        assertThat(resultRow.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void departmentFinalResultCannotBeOverwritten() {
        BizResult resultRow = new BizResult();
        resultRow.setId(5L);
        resultRow.setOwnerUserId(10L);
        resultRow.setStatus("CONFIRMED");
        resultRow.setDeleted(0);
        when(resultMapper.selectById(5L)).thenReturn(resultRow);

        BizException error = catchThrowableOfType(
                () -> departmentService.rejectResult(departmentOwner, "5", new PerformanceActionReqDTO()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("不能重复确认或驳回");
        assertThat(resultRow.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void leaderCannotSuggestAfterFinalConfirmation() {
        BizResult resultRow = new BizResult();
        resultRow.setId(6L);
        resultRow.setOwnerUserId(10L);
        resultRow.setStatus("CONFIRMED");
        resultRow.setDeleted(0);
        when(resultMapper.selectById(6L)).thenReturn(resultRow);

        BizException error = catchThrowableOfType(
                () -> leaderService.submitResultSuggestion(leader, "6", new PerformanceActionReqDTO()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("不能再提交确认建议");
    }

    @Test
    void leaderCannotSubmitResultSuggestionTwice() {
        BizResult resultRow = new BizResult();
        resultRow.setId(61L);
        resultRow.setOwnerUserId(10L);
        resultRow.setStatus("PENDING");
        resultRow.setSuggestionStatus("SUGGEST_CONFIRM");
        resultRow.setDeleted(0);
        when(resultMapper.selectById(61L)).thenReturn(resultRow);
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setDecision("SUGGEST_REJECT");
        request.setComment("再次修改建议");

        BizException error = catchThrowableOfType(
                () -> leaderService.submitResultSuggestion(leader, "61", request),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        assertThat(error.getMessage()).contains("不能重复处理");
        verify(resultMapper, never()).updateById(resultRow);
    }

    @Test
    void exportIntegrityCheckPersistsVerifiedStatus() {
        BizExportTask task = exportTask("EXP-1", "SUCCESS");
        task.setFilePath("EXP-1/EXP-1.zip");
        task.setIntegrityStatus("COMPLETE");
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);
        when(exportFileService.verify(task)).thenReturn(true);

        var action = departmentService.checkExportTask(departmentOwner, task.getId());

        assertThat(action.status()).isEqualTo("CHECKED");
        assertThat(task.getIntegrityStatus()).isEqualTo("VERIFIED");
        verify(exportTaskMapper).selectForUpdateById(task.getId());
        verify(exportTaskMapper).updateById(task);
    }

    @Test
    void exportIntegrityCheckKeepsEvidencePackageIncomplete() {
        BizExportTask task = exportTask("EXP-2", "SUCCESS");
        task.setFilePath("EXP-2/EXP-2.zip");
        task.setIntegrityStatus("INCOMPLETE");
        task.setMissingItems("missing-items");
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);
        when(exportFileService.verify(task)).thenReturn(true);
        when(jsonCodec.stringList("missing-items")).thenReturn(List.of("成果 31：完整性校验失败"));

        var action = departmentService.checkExportTask(departmentOwner, task.getId());

        assertThat(action.status()).isEqualTo("INCOMPLETE");
        assertThat(task.getStatus()).isEqualTo("SUCCESS");
        assertThat(task.getIntegrityStatus()).isEqualTo("INCOMPLETE");
        assertThat(task.getErrorMessage()).contains("1 项");
        verify(exportTaskMapper).updateById(task);
    }

    @Test
    void exportIntegrityMismatchPersistsReviewStatusWithoutRollbackSignal() {
        BizExportTask task = exportTask("EXP-MISMATCH", "SUCCESS");
        task.setFilePath("EXP-MISMATCH/EXP-MISMATCH.zip");
        task.setIntegrityStatus("COMPLETE");
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);
        when(exportFileService.verify(task)).thenReturn(false);

        var action = departmentService.checkExportTask(departmentOwner, task.getId());

        assertThat(action.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(task.getStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(task.getIntegrityStatus()).isEqualTo("MISMATCH");
        assertThat(task.getErrorMessage()).contains("不一致");
        verify(exportTaskMapper).updateById(task);
    }

    @Test
    void failedExportRetryResetsArtifactsAndSchedulesWorker() {
        BizExportTask task = exportTask("EXP-2", "FAILED");
        task.setIntegrityStatus("FAILED");
        task.setFileName("old.zip");
        task.setFilePath("EXP-2/old.zip");
        task.setChecksum("old-checksum");
        task.setExpireAt(LocalDateTime.now().plusDays(1));
        task.setErrorMessage("生成失败");
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);

        var action = departmentService.retryExportTask(departmentOwner, task.getId());

        assertThat(action.status()).isEqualTo("PENDING");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getIntegrityStatus()).isEqualTo("PENDING_CHECK");
        assertThat(task.getFileName()).isNull();
        assertThat(task.getFilePath()).isNull();
        assertThat(task.getChecksum()).isNull();
        assertThat(task.getExpireAt()).isNull();
        assertThat(task.getSizeText()).isEqualTo("--");
        verify(exportTaskMapper).selectForUpdateById(task.getId());
        verify(exportFileService).deleteTaskFiles(task);
        verify(exportTaskWorker).generate(task.getId());
    }

    @Test
    void exportRetryStartsWorkerOnlyAfterTransactionCommit() {
        BizExportTask task = exportTask("EXP-AFTER-COMMIT", "FAILED");
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);
        TransactionSynchronizationManager.initSynchronization();
        try {
            departmentService.retryExportTask(departmentOwner, task.getId());

            verify(exportTaskWorker, never()).generate(task.getId());
            for (TransactionSynchronization synchronization
                    : TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCommit();
            }
            verify(exportTaskWorker).generate(task.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void expiredExportDownloadInfoReturnsGone() {
        BizExportTask task = exportTask("EXP-3", "SUCCESS");
        task.setExpireAt(LocalDateTime.now().minusMinutes(1));
        when(exportTaskMapper.selectById(task.getId())).thenReturn(task);

        BizException error = catchThrowableOfType(
                () -> departmentService.exportDownloadInfo(departmentOwner, task.getId()),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(410);
        assertThat(error.getMessage()).contains("已过期");
    }

    @Test
    void openingTodoMovesItIntoProcessingState() {
        BizTodo todo = new BizTodo();
        todo.setId(41L);
        todo.setReceiverId(departmentOwner.userId());
        todo.setStatus("UNREAD");
        todo.setDeleted(0);
        when(todoMapper.selectById(41L)).thenReturn(todo);

        var action = departmentService.readTodo(departmentOwner, "41");

        assertThat(action.status()).isEqualTo("READ");
        assertThat(todo.getStatus()).isEqualTo("READ");
        verify(todoMapper).selectForUpdateById(todo.getId());
        verify(todoMapper).updateById(todo);
    }

    @Test
    void completedTodoCannotBeRemindedOrEscalated() {
        BizTodo todo = new BizTodo();
        todo.setId(42L);
        todo.setReceiverId(departmentOwner.userId());
        todo.setStatus("DONE");
        todo.setDeleted(0);
        when(todoMapper.selectById(todo.getId())).thenReturn(todo);

        BizException remindError = catchThrowableOfType(
                () -> departmentService.remindTodo(departmentOwner, "42"),
                BizException.class
        );
        BizException escalateError = catchThrowableOfType(
                () -> departmentService.escalateTodo(departmentOwner, "42"),
                BizException.class
        );

        assertThat(remindError.getCode()).isEqualTo(409);
        assertThat(escalateError.getCode()).isEqualTo(409);
        assertThat(todo.getStatus()).isEqualTo("DONE");
        verify(todoMapper, never()).updateById(todo);
    }

    @Test
    void todoCannotBeMarkedForEscalationTwice() {
        BizTodo todo = new BizTodo();
        todo.setId(43L);
        todo.setReceiverId(departmentOwner.userId());
        todo.setStatus("READ");
        todo.setImpactText("影响审批时效；已标记升级处理");
        todo.setDeleted(0);
        when(todoMapper.selectById(todo.getId())).thenReturn(todo);

        BizException error = catchThrowableOfType(
                () -> departmentService.escalateTodo(departmentOwner, "43"),
                BizException.class
        );

        assertThat(error.getCode()).isEqualTo(409);
        verify(todoMapper, never()).updateById(todo);
    }

    @Test
    void departmentOwnerResolvesAppealAndCompletesTodo() {
        BizEmployeeAppeal appeal = new BizEmployeeAppeal();
        appeal.setId(51L);
        appeal.setOwnerUserId(10L);
        appeal.setHandlerId(departmentOwner.userId());
        appeal.setStatus("PROCESSING");
        appeal.setDeleted(0);
        when(appealMapper.selectById(51L)).thenReturn(appeal);
        when(todoMapper.selectList(any())).thenReturn(List.of());
        PerformanceActionReqDTO request = new PerformanceActionReqDTO();
        request.setDecision("MAINTAIN");
        request.setComment("现有确认结论与证据一致");

        var action = departmentService.resolveAppeal(departmentOwner, "51", request);

        assertThat(action.status()).isEqualTo("RESOLVED");
        assertThat(appeal.getStatus()).isEqualTo("RESOLVED");
        assertThat(appeal.getHandleComment()).contains("维持原结果", "现有确认结论与证据一致");
        assertThat(appeal.getHandledAt()).isNotNull();
        verify(appealMapper).updateById(appeal);
    }

    private BizExportTask exportTask(String id, String status) {
        BizExportTask task = new BizExportTask();
        task.setId(id);
        task.setStatus(status);
        task.setRequestedBy(departmentOwner.userId());
        task.setDeptId(departmentOwner.deptId());
        task.setDeleted(0);
        return task;
    }
}
