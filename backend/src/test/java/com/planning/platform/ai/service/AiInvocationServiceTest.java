package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.provider.AiProvider;
import com.planning.platform.ai.provider.AiProviderRegistry;
import com.planning.platform.common.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiInvocationServiceTest {

    @Mock AiRepository repository;
    @Mock AiCryptoService cryptoService;
    @Mock AiProviderRegistry providerRegistry;
    @Mock AiProvider provider;
    @Mock AiRateLimitService rateLimitService;

    private ObjectMapper objectMapper;
    private AiInvocationService service;
    private AuthUser employee;
    private AiRepository.ModelConfig config;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new AiInvocationService(repository, cryptoService, providerRegistry,
                new AiRedactionService(), rateLimitService, objectMapper);
        employee = new AuthUser(7L, "employee", "员工", 110L, 110L,
                false, List.of("EMPLOYEE"), List.of());
        config = new AiRepository.ModelConfig(1L, "mock", "MOCK", "", "", "planning-mock-v1",
                30, true, true, true, true, "", "", 10, 30, 20, 1, "ENABLED");
    }

    @Test
    void returnsStoredIdempotentResponseWithoutCallingProvider() throws Exception {
        AiModels.GenerateResponse stored = new AiModels.GenerateResponse(
                "AI-STORED", "cached", List.of(), List.of(), List.of(), AiModels.NOTICE);
        String json = objectMapper.writeValueAsString(stored);
        when(repository.successfulOutput(7L, AiModels.MONTH_PLAN_DRAFT, "req-1"))
                .thenReturn(Optional.of(json));
        when(repository.fromJson(json, AiModels.GenerateResponse.class)).thenReturn(stored);

        AiModels.GenerateResponse result = service.invoke(
                employee, "req-1", AiModels.MONTH_PLAN_DRAFT, "MONTH_PLAN", null,
                objectMapper.createObjectNode(),
                (content, suggestionId) -> { throw new AssertionError("validator must not run"); },
                AiModels.GenerateResponse.class, UnaryOperator.identity());

        assertThat(result.summary()).isEqualTo("cached");
        verifyNoInteractions(provider);
        verify(rateLimitService, never()).consume(any(), any(), any(Integer.class));
    }

    @Test
    void repairsInvalidOutputOnceAndLogsCallerBusinessType() {
        ObjectNode context = objectMapper.createObjectNode().put("planMonth", "2026-08");
        when(repository.toJson(any())).thenAnswer(invocation ->
                objectMapper.writeValueAsString(invocation.getArgument(0)));
        when(repository.successfulOutput(7L, AiModels.MONTH_PLAN_DRAFT, "req-2"))
                .thenReturn(Optional.empty());
        when(repository.requireActiveConfig()).thenReturn(config);
        when(repository.requirePrompt(AiModels.MONTH_PLAN_DRAFT)).thenReturn(
                new AiRepository.PromptTemplate(1L, AiModels.MONTH_PLAN_DRAFT, "v1", "system", "user", "v1"));
        when(repository.newSuggestionId()).thenReturn("AI-REPAIRED");
        when(providerRegistry.require("MOCK")).thenReturn(provider);
        when(cryptoService.decrypt("")).thenReturn("");
        when(provider.complete(any(), any())).thenReturn(
                new AiProvider.ProviderResponse("invalid", 3, 2),
                new AiProvider.ProviderResponse("valid", 4, 3));

        AiModels.GenerateResponse result = service.invoke(
                employee, "req-2", AiModels.MONTH_PLAN_DRAFT, "WEEK_PLAN", 99L, context,
                (content, suggestionId) -> {
                    if ("invalid".equals(content)) throw new AiOutputValidator.OutputException("bad json");
                    return new AiModels.GenerateResponse(suggestionId, "fixed", List.of(),
                            List.of(), List.of(), AiModels.NOTICE);
                },
                AiModels.GenerateResponse.class, UnaryOperator.identity());

        assertThat(result.summary()).isEqualTo("fixed");
        verify(provider, times(2)).complete(any(), any());
        ArgumentCaptor<AiRepository.CallRecord> captor = ArgumentCaptor.forClass(AiRepository.CallRecord.class);
        verify(repository).saveSuccess(captor.capture());
        assertThat(captor.getValue().bizType()).isEqualTo("WEEK_PLAN");
        assertThat(captor.getValue().bizId()).isEqualTo(99L);
        assertThat(captor.getValue().inputTokens()).isEqualTo(7);
        assertThat(captor.getValue().outputTokens()).isEqualTo(5);
    }
}
