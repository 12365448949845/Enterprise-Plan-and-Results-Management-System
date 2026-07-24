package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.config.AiProperties;
import com.planning.platform.ai.domain.BizAiReview;
import com.planning.platform.ai.mapper.BizAiReviewMapper;
import com.planning.platform.ai.model.AiReviewModels.ReviewResult;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.mapper.BizAcceptanceStandardMapper;
import com.planning.platform.performance.mapper.BizDeliverableTemplateMapper;
import com.planning.platform.performance.service.PerformanceDataScopeService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewAccessTest {

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
    @Spy private AiProperties properties = new AiProperties();
    @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Spy private AiCompletionCalculator completionCalculator = new AiCompletionCalculator();

    @InjectMocks private AiReviewService service;

    @BeforeEach
    void setUp() throws Exception {
        BizAiReview review = new BizAiReview();
        review.setId(1L);
        review.setBizType("DAY_PLAN");
        review.setBizId(99L);
        review.setOwnerUserId(20L);
        review.setReviewStatus("RULE_ONLY");
        review.setOverallRisk("LOW");
        review.setProvider("qwen");
        review.setModelName("qwen-plus");
        review.setPromptVersion("v1");
        review.setCreatedAt(LocalDateTime.now());
        review.setDeleted(0);
        review.setResultJson(objectMapper.writeValueAsString(new ReviewResult(
                "LOW", "检查完成", List.of(), List.of(), List.of(), null, null, null, null, null)));
        when(reviewMapper.selectOne(any())).thenReturn(review);
    }

    @Test
    void ordinaryEmployeeCannotReadCoworkerReviewEvenInSameDepartment() {
        AuthUser employee = new AuthUser(10L, "employee", "员工甲", 110L, null,
                false, List.of("EMPLOYEE"), List.of());
        when(dataScopeService.directLeaderId(20L)).thenReturn(30L);

        BizException error = catchThrowableOfType(
                () -> service.latestForViewer(employee, "DAY_PLAN", 99L), BizException.class);

        assertThat(error.getCode()).isEqualTo(403);
        verify(dataScopeService, never()).departmentOwnerIds(any(), any());
    }

    @Test
    void configuredDirectLeaderCanReadEmployeeReview() {
        AuthUser leader = new AuthUser(30L, "leader", "直属领导", 110L, null,
                false, List.of("DIRECT_LEADER"), List.of());
        when(dataScopeService.directLeaderId(20L)).thenReturn(30L);

        var review = service.latestForViewer(leader, "DAY_PLAN", 99L);

        assertThat(review.id()).isEqualTo(1L);
        assertThat(review.result().summary()).isEqualTo("检查完成");
    }
}
