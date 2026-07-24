package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.provider.AiProviderRegistry;
import com.planning.platform.ai.provider.AiProvider;
import com.planning.platform.ai.provider.MockAiProvider;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.performance.mapper.BizAcceptanceStandardMapper;
import com.planning.platform.performance.mapper.BizDeliverableTemplateMapper;
import com.planning.platform.performance.service.PerformanceDataScopeService;
import com.planning.platform.planning.domain.BizMonthPlan;
import com.planning.platform.planning.domain.BizMonthPlanItem;
import com.planning.platform.planning.mapper.BizMonthPlanItemMapper;
import com.planning.platform.planning.mapper.BizMonthPlanMapper;
import com.planning.platform.system.domain.SysUser;
import com.planning.platform.system.mapper.SysUserMapper;
import com.planning.platform.system.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MonthPlanAiServiceTest {

    @Mock AiRepository repository;
    @Mock AiCryptoService cryptoService;
    @Mock AiProviderRegistry providerRegistry;
    @Mock AiProvider provider;
    @Mock AiRateLimitService rateLimitService;
    @Mock SysUserMapper userMapper;
    @Mock BizMonthPlanMapper monthPlanMapper;
    @Mock BizMonthPlanItemMapper monthPlanItemMapper;
    @Mock BizDeliverableTemplateMapper templateMapper;
    @Mock BizAcceptanceStandardMapper standardMapper;
    @Mock PerformanceDataScopeService dataScopeService;
    @Mock AuditLogService auditLogService;

    private ObjectMapper objectMapper;
    private AiInvocationService invocationService;
    private MonthPlanAiService service;
    private AuthUser employee;
    private AiRepository.ModelConfig config;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        invocationService = new AiInvocationService(repository, cryptoService, providerRegistry,
                new AiRedactionService(), rateLimitService, objectMapper);
        service = new MonthPlanAiService(repository, invocationService,
                new AiOutputValidator(objectMapper), rateLimitService, objectMapper,
                userMapper, monthPlanMapper, monthPlanItemMapper, templateMapper, standardMapper,
                dataScopeService, auditLogService);
        employee = new AuthUser(10L, "employee", "员工", 110L, 110L,
                false, List.of("EMPLOYEE"), List.of());
        config = new AiRepository.ModelConfig(1L, "mock", "MOCK", "", "", "planning-mock-v1",
                30, true, true, true, true, "", "", 10, 30, 20, 1, "ENABLED");
        SysUser sysUser = new SysUser();
        sysUser.setId(10L); sysUser.setDeptId(110L); sysUser.setDeleted(0);
        when(userMapper.selectById(10L)).thenReturn(sysUser);
        when(monthPlanMapper.selectList(any())).thenReturn(List.of());
        when(templateMapper.selectList(any())).thenReturn(List.of());
        when(repository.planContext(110L, YearMonth.now().plusMonths(1).toString())).thenReturn(Optional.empty());
        when(dataScopeService.departmentName(110L)).thenReturn("产品一组");
        when(repository.toJson(any())).thenAnswer(invocation -> objectMapper.writeValueAsString(invocation.getArgument(0)));
    }

    @Test
    void generatedSuggestionNeverWritesMonthPlanBusinessTables() {
        String month = YearMonth.now().plusMonths(1).toString();
        when(repository.successfulOutput(10L, AiModels.MONTH_PLAN_DRAFT, "req-1")).thenReturn(Optional.empty());
        when(repository.requireActiveConfig()).thenReturn(config);
        when(repository.requirePrompt(AiModels.MONTH_PLAN_DRAFT)).thenReturn(new AiRepository.PromptTemplate(
                1L, AiModels.MONTH_PLAN_DRAFT, "v1", "system", "user", "v1"));
        when(repository.newSuggestionId()).thenReturn("AI-TEST-1");
        when(providerRegistry.require("MOCK")).thenReturn(new MockAiProvider(objectMapper));
        when(cryptoService.decrypt("")).thenReturn("");

        AiModels.GenerateResponse response = service.generate(employee,
                new AiModels.GenerateRequest("req-1", month, "完成客户方案；推进产品上线",
                        new AiModels.PlanForm("", List.of()), "产品经理"));

        assertThat(response.items()).hasSize(2);
        assertThat(response.suggestionId()).isEqualTo("AI-TEST-1");
        verify(repository).saveSuccess(any(AiRepository.CallRecord.class));
        verify(monthPlanMapper, never()).insert(any(BizMonthPlan.class));
        verify(monthPlanMapper, never()).updateById(any(BizMonthPlan.class));
        verify(monthPlanItemMapper, never()).insert(any(BizMonthPlanItem.class));
        verify(monthPlanItemMapper, never()).updateById(any(BizMonthPlanItem.class));
    }

    @Test
    void repeatedRequestReturnsStoredSuggestionWithoutCallingProvider() throws Exception {
        String month = YearMonth.now().plusMonths(1).toString();
        AiModels.GenerateResponse stored = new AiModels.GenerateResponse("AI-STORED", "摘要", List.of(), List.of(), List.of(), AiModels.NOTICE);
        String json = objectMapper.writeValueAsString(stored);
        when(repository.successfulOutput(10L, AiModels.MONTH_PLAN_DRAFT, "same-request")).thenReturn(Optional.of(json));
        when(repository.fromJson(json, AiModels.GenerateResponse.class)).thenReturn(stored);

        AiModels.GenerateResponse response = service.generate(employee,
                new AiModels.GenerateRequest("same-request", month, "不会再次调用",
                        new AiModels.PlanForm("", List.of()), "产品经理"));

        assertThat(response.suggestionId()).isEqualTo("AI-STORED");
        verify(providerRegistry, never()).require(anyString());
        verify(repository, never()).saveSuccess(any());
    }

    @Test
    void userOutsideGrayScopeCannotInvokeModel() {
        String month = YearMonth.now().plusMonths(1).toString();
        AiRepository.ModelConfig grayConfig = new AiRepository.ModelConfig(1L, "mock", "MOCK", "", "", "mock",
                30, true, true, true, true, "999", "888", 10, 30, 20, 1, "ENABLED");
        when(repository.successfulOutput(10L, AiModels.MONTH_PLAN_DRAFT, "req-gray")).thenReturn(Optional.empty());
        when(repository.requireActiveConfig()).thenReturn(grayConfig);

        assertThatThrownBy(() -> service.generate(employee,
                new AiModels.GenerateRequest("req-gray", month, "完成重点任务",
                        new AiModels.PlanForm("", List.of()), "产品经理")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("灰度范围");
        verify(providerRegistry, never()).require(anyString());
    }

    @Test
    void invalidStructuredOutputIsRepairedOnceBeforeReturning() {
        String month = YearMonth.now().plusMonths(1).toString();
        String validJson = """
                {"summary":"修复后的计划","items":[{"workType":"TASK","taskName":"完成任务",
                "taskContent":"完成任务并形成闭环","deliverable":"任务成果材料",
                "deadline":"%s","performanceWeight":100}],"warnings":[]}
                """.formatted(YearMonth.parse(month).atEndOfMonth());
        when(repository.successfulOutput(10L, AiModels.MONTH_PLAN_DRAFT, "repair-request")).thenReturn(Optional.empty());
        when(repository.requireActiveConfig()).thenReturn(config);
        when(repository.requirePrompt(AiModels.MONTH_PLAN_DRAFT)).thenReturn(new AiRepository.PromptTemplate(
                1L, AiModels.MONTH_PLAN_DRAFT, "v1", "system", "user", "v1"));
        when(repository.newSuggestionId()).thenReturn("AI-REPAIRED");
        when(providerRegistry.require("MOCK")).thenReturn(provider);
        when(cryptoService.decrypt("")).thenReturn("");
        when(provider.complete(any(), any())).thenReturn(
                new AiProvider.ProviderResponse("not-json", 10, 2),
                new AiProvider.ProviderResponse(validJson, 12, 20));

        AiModels.GenerateResponse response = service.generate(employee,
                new AiModels.GenerateRequest("repair-request", month, "完成任务",
                        new AiModels.PlanForm("", List.of()), "产品经理"));

        assertThat(response.summary()).isEqualTo("修复后的计划");
        org.mockito.Mockito.verify(provider, org.mockito.Mockito.times(2)).complete(any(), any());
        verify(repository).saveSuccess(any(AiRepository.CallRecord.class));
    }

    @Test
    void optimizeRequestAlwaysIncludesTheFixedOutputContract() {
        String month = YearMonth.now().plusMonths(1).toString();
        String validJson = validOptimizeJson(month);
        when(repository.successfulOutput(10L, AiModels.MONTH_PLAN_ITEM_OPTIMIZE, "optimize-contract"))
                .thenReturn(Optional.empty());
        when(repository.requireActiveConfig()).thenReturn(config);
        when(repository.requirePrompt(AiModels.MONTH_PLAN_ITEM_OPTIMIZE)).thenReturn(new AiRepository.PromptTemplate(
                2L, AiModels.MONTH_PLAN_ITEM_OPTIMIZE, "v1", "system", "优化当前任务", "v1"));
        when(repository.newSuggestionId()).thenReturn("AI-OPTIMIZE-1");
        when(providerRegistry.require("MOCK")).thenReturn(provider);
        when(cryptoService.decrypt("")).thenReturn("");
        when(provider.complete(any(), any())).thenReturn(new AiProvider.ProviderResponse(validJson, 20, 30));

        service.optimize(employee, optimizeRequest("optimize-contract", month));

        ArgumentCaptor<AiProvider.ProviderRequest> requestCaptor = ArgumentCaptor.forClass(AiProvider.ProviderRequest.class);
        verify(provider).complete(any(), requestCaptor.capture());
        assertThat(requestCaptor.getValue().userPrompt())
                .contains("\"item\"")
                .contains("\"warnings\"")
                .contains("performanceWeight 必须是 JSON 数字")
                .contains("deadline 必须属于 CONTEXT_JSON.planMonth")
                .contains(YearMonth.parse(month).atEndOfMonth().toString());
    }

    @Test
    void optimizeRepairRequestIncludesValidationReasonAndCompleteContract() {
        String month = YearMonth.now().plusMonths(1).toString();
        when(repository.successfulOutput(10L, AiModels.MONTH_PLAN_ITEM_OPTIMIZE, "optimize-repair"))
                .thenReturn(Optional.empty());
        when(repository.requireActiveConfig()).thenReturn(config);
        when(repository.requirePrompt(AiModels.MONTH_PLAN_ITEM_OPTIMIZE)).thenReturn(new AiRepository.PromptTemplate(
                2L, AiModels.MONTH_PLAN_ITEM_OPTIMIZE, "v1", "system", "优化当前任务", "v1"));
        when(repository.newSuggestionId()).thenReturn("AI-OPTIMIZE-2");
        when(providerRegistry.require("MOCK")).thenReturn(provider);
        when(cryptoService.decrypt("")).thenReturn("");
        when(provider.complete(any(), any())).thenReturn(
                new AiProvider.ProviderResponse("{\"warnings\":[]}", 10, 3),
                new AiProvider.ProviderResponse(validOptimizeJson(month), 20, 30));

        service.optimize(employee, optimizeRequest("optimize-repair", month));

        ArgumentCaptor<AiProvider.ProviderRequest> requestCaptor = ArgumentCaptor.forClass(AiProvider.ProviderRequest.class);
        verify(provider, times(2)).complete(any(), requestCaptor.capture());
        AiProvider.ProviderRequest repairRequest = requestCaptor.getAllValues().get(1);
        assertThat(repairRequest.userPrompt())
                .contains("上次输出未通过校验：item 必须为对象")
                .contains("performanceWeight 必须是 JSON 数字")
                .contains("重新生成完整 JSON");
    }

    private AiModels.OptimizeRequest optimizeRequest(String requestId, String month) {
        AiModels.PlanItem item = new AiModels.PlanItem("TASK", "原任务", "原任务内容", "原交付物",
                YearMonth.parse(month).atEndOfMonth(), java.math.BigDecimal.valueOf(20));
        return new AiModels.OptimizeRequest(requestId, month, "计划摘要", item, "优化表达", "产品经理");
    }

    private String validOptimizeJson(String month) {
        return """
                {"item":{"workType":"TASK","taskName":"优化后的任务",
                "taskContent":"优化后的任务内容","deliverable":"优化后的交付物",
                "deadline":"%s","performanceWeight":20},"warnings":[]}
                """.formatted(YearMonth.parse(month).atEndOfMonth());
    }
}
