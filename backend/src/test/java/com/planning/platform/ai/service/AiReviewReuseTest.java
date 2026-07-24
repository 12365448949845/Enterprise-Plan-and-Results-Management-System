package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.domain.BizAiReview;
import com.planning.platform.ai.mapper.BizAiReviewMapper;
import com.planning.platform.ai.model.AiReviewModels.ModelAnalysis;
import com.planning.platform.ai.model.AiReviewModels.AnalysisDimension;
import com.planning.platform.ai.model.AiReviewModels.AnalysisRequest;
import com.planning.platform.ai.model.AiReviewModels.Issue;
import com.planning.platform.ai.model.AiReviewModels.ReviewResult;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.mapper.BizAcceptanceStandardMapper;
import com.planning.platform.performance.mapper.BizDeliverableTemplateMapper;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.mapper.BizDayPlanMapper;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.planning.mapper.BizResultEvidenceMapper;
import com.planning.platform.planning.mapper.BizResultMapper;
import com.planning.platform.planning.mapper.BizWeekPlanItemMapper;
import com.planning.platform.planning.mapper.BizWeekPlanMapper;
import com.planning.platform.system.service.AuditLogService;
import com.planning.platform.system.service.WorkdayCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewReuseTest {

    @Mock private BizAiReviewMapper reviewMapper;
    @Mock private BizMonthPlanMapper monthPlanMapper;
    @Mock private BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock private BizWeekPlanMapper weekPlanMapper;
    @Mock private BizWeekPlanItemMapper weekPlanItemMapper;
    @Mock private BizDayPlanMapper dayPlanMapper;
    @Mock private BizResultMapper resultMapper;
    @Mock private BizResultEvidenceMapper resultEvidenceMapper;
    @Mock private BizDeliverableTemplateMapper templateMapper;
    @Mock private BizAcceptanceStandardMapper acceptanceStandardMapper;
    @Mock private PerformanceDataScopeService dataScopeService;
    @Mock private WorkdayCalendarService workdayCalendarService;
    @Mock private EvidenceDocumentService evidenceDocumentService;
    @Mock private AiRateLimitService rateLimitService;
    @Mock private QwenAiClient qwenAiClient;
    @Mock private AuditLogService auditLogService;
    @Spy private AiCompletionCalculator completionCalculator = new AiCompletionCalculator();
    @Spy private AiGroundingService groundingService = new AiGroundingService();
    @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @InjectMocks private AiReviewService service;
    private BizMonthPlanItem planItem;

    private final AuthUser employee = new AuthUser(4L, "employee", "演示员工", 110L, null,
            false, List.of("EMPLOYEE"), List.of());

    @BeforeEach
    void setUp() {
        BizMonthPlan plan = new BizMonthPlan();
        plan.setId(14L);
        plan.setTitle("AI检查计划");
        plan.setPlanMonth("2027-02");
        plan.setContent("验证AI检查复用");
        plan.setOwnerUserId(4L);
        plan.setDeptId(110L);
        plan.setStatus("DRAFT");
        plan.setVersionNo(1);
        plan.setDeleted(0);

        planItem = new BizMonthPlanItem();
        planItem.setId(11L);
        planItem.setMonthPlanId(14L);
        planItem.setTaskType("REGULAR");
        planItem.setTaskName("导入接口");
        planItem.setTaskContent("开发客户数据导入接口并完成联调");
        planItem.setDeliverable("接口代码和测试记录");
        planItem.setDeadline(LocalDate.of(2027, 2, 12));
        planItem.setPerformanceWeight(new BigDecimal("100"));
        planItem.setDeleted(0);

        when(monthPlanMapper.selectById(14L)).thenReturn(plan);
        when(monthPlanItemMapper.selectList(any())).thenAnswer(ignored -> List.of(planItem));
        when(templateMapper.selectList(any())).thenReturn(List.of());
        when(qwenAiClient.available()).thenReturn(true);
        when(qwenAiClient.provider()).thenReturn("ALIYUN");
        when(qwenAiClient.model()).thenReturn("qwen3.7-plus");
        when(qwenAiClient.promptVersion()).thenReturn("v9-redacted-grounded");
        lenient().when(qwenAiClient.checkDailyLimit()).thenReturn(20);
        lenient().when(qwenAiClient.permitted(4L, 110L)).thenReturn(true);
    }

    @Test
    void reusesRecentModelFailureWithoutCallingProviderAgain() throws Exception {
        BizAiReview failed = review(41L, "MODEL_FAILED", LocalDateTime.now().minusSeconds(30));
        when(reviewMapper.selectOne(any())).thenReturn(null, failed);

        var result = service.ensurePlanReview(employee, "MONTH_PLAN", 14L);

        assertThat(result.id()).isEqualTo(41L);
        assertThat(result.status()).isEqualTo("MODEL_FAILED");
        verify(qwenAiClient, never()).analyze(any(), any());
        verify(rateLimitService, never()).consume(any(), any(), anyInt());
        verify(reviewMapper, never()).insert(any(BizAiReview.class));
    }

    @Test
    void runsProviderWhenNoReusableReviewExists() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(qwenAiClient.analyze(any(), any())).thenReturn(
                new ModelAnalysis("检查完成", List.of(), List.of(), List.of()));

        var result = service.ensurePlanReview(employee, "MONTH_PLAN", 14L);

        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(qwenAiClient).analyze(any(), any());
        verify(reviewMapper).insert(any(BizAiReview.class));
    }

    @Test
    void dailyLimitStopsProviderCallAndReturnsModelFailedReport() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        doThrow(new BizException(429, "今日 AI 调用次数已达上限"))
                .when(rateLimitService).consume(4L, AiRateLimitService.AI_REVIEW_CHECK, 20);

        var result = service.ensurePlanReview(employee, "MONTH_PLAN", 14L);

        assertThat(result.status()).isEqualTo("MODEL_FAILED");
        assertThat(result.errorMessage()).contains("已达上限");
        verify(qwenAiClient, never()).analyze(any(), any());
        verify(reviewMapper).insert(any(BizAiReview.class));
    }

    @Test
    void userOutsideAllowListGetsRuleOnlyReportAndCannotReuseModelSuccess() {
        when(qwenAiClient.permitted(4L, 110L)).thenReturn(false);
        when(reviewMapper.selectOne(any())).thenReturn(null);

        var result = service.ensurePlanReview(employee, "MONTH_PLAN", 14L);

        assertThat(result.status()).isEqualTo("RULE_ONLY");
        assertThat(result.errorMessage()).contains("灰度范围");
        verify(qwenAiClient, never()).analyze(any(), any());
        verify(rateLimitService, never()).consume(any(), any(), anyInt());
        verify(reviewMapper).insert(any(BizAiReview.class));
    }

    @Test
    void keepsSeparateRisksForDifferentRulesOnTheSameField() {
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(qwenAiClient.analyze(any(), any())).thenAnswer(invocation -> {
            AnalysisRequest request = invocation.getArgument(0);
            var source = request.sourceCatalog().stream()
                    .filter(item -> item.path().equals("businessData.items[0].taskContent"))
                    .findFirst().orElseThrow();
            Issue vague = new Issue("AI_SEMANTIC_RISK", "AI", "MEDIUM", source.path(), "内容不够具体",
                    "SEM_PLAN_01", source.content(), "行动对象不够明确。", "补充具体对象。", 0.8,
                    List.of(source.id()));
            Issue workload = new Issue("AI_SEMANTIC_RISK", "AI", "MEDIUM", source.path(), "范围依据不足",
                    "SEM_PLAN_03", source.content(), "任务范围与权重关系需要核对。", "核对范围和权重。", 0.8,
                    List.of(source.id()));
            return new ModelAnalysis("检查完成", List.of(vague, workload), List.of(
                    new AnalysisDimension("SEM_PLAN_01", "任务具体性", "RISK", "内容需要补充。",
                            source.content(), "行动对象不够明确。", 0.8, List.of(source.id())),
                    new AnalysisDimension("SEM_PLAN_03", "范围与权重", "RISK", "范围与权重需要核对。",
                            source.content(), "任务范围与权重关系需要核对。", 0.8, List.of(source.id()))
            ), List.of());
        });

        var result = service.ensurePlanReview(employee, "MONTH_PLAN", 14L);

        assertThat(result.result().issues()).extracting(Issue::ruleId)
                .contains("SEM_PLAN_01", "SEM_PLAN_03");
    }

    @Test
    void latestReportIsMarkedStaleAfterPlanContentChanges() {
        AtomicReference<BizAiReview> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            BizAiReview review = invocation.getArgument(0);
            review.setId(52L);
            stored.set(review);
            return 1;
        }).when(reviewMapper).insert(any(BizAiReview.class));
        when(reviewMapper.selectOne(any())).thenAnswer(ignored -> stored.get());
        when(qwenAiClient.analyze(any(), any())).thenReturn(
                new ModelAnalysis("检查完成", List.of(), List.of(), List.of()));

        var checked = service.checkPlan(employee, "MONTH_PLAN", 14L);
        assertThat(checked.stale()).isFalse();

        planItem.setTaskContent("开发客户数据导入接口、补充回滚方案并完成联调");
        var latest = service.latestForViewer(employee, "MONTH_PLAN", 14L);

        assertThat(latest.stale()).isTrue();
    }

    private BizAiReview review(Long id, String status, LocalDateTime createdAt) throws Exception {
        BizAiReview review = new BizAiReview();
        review.setId(id);
        review.setBizType("MONTH_PLAN");
        review.setBizId(14L);
        review.setBizVersion("1");
        review.setContentHash("same-content");
        review.setOwnerUserId(4L);
        review.setDeptId(110L);
        review.setTriggerSource("EMPLOYEE_CHECK");
        review.setReviewStatus(status);
        review.setOverallRisk("LOW");
        review.setProvider("ALIYUN");
        review.setModelName("qwen3.7-plus");
        review.setPromptVersion("v9-redacted-grounded");
        review.setErrorMessage("AI服务暂时不可用");
        review.setResultJson(objectMapper.writeValueAsString(new ReviewResult(
                "LOW", "AI未完成", List.of(), List.of(), List.of(), null, null, null, null, null)));
        review.setCreatedAt(createdAt);
        review.setUpdatedAt(createdAt);
        review.setDeleted(0);
        return review;
    }
}
