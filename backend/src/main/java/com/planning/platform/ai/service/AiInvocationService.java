package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.platform.ai.provider.AiProvider;
import com.planning.platform.ai.provider.AiProviderRegistry;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiInvocationService {

    private final AiRepository repository;
    private final AiCryptoService cryptoService;
    private final AiProviderRegistry providerRegistry;
    private final AiRedactionService redactionService;
    private final AiRateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public <T> T invoke(AuthUser user, String requestId, String sceneCode,
                        String bizType, Long bizId, ObjectNode rawContext,
                        BiFunction<String, String, T> validator,
                        Class<T> responseType, UnaryOperator<String> promptDecorator) {
        Optional<String> existing = repository.successfulOutput(user.userId(), sceneCode, requestId);
        if (existing.isPresent()) return repository.fromJson(existing.get(), responseType);

        AiRepository.ModelConfig config = repository.requireActiveConfig();
        requireAvailable(config, user, sceneCode);
        rateLimitService.consume(user.userId(), sceneCode, config.limitFor(sceneCode));
        AiRepository.PromptTemplate prompt = repository.requirePrompt(sceneCode);
        JsonNode context = redactionService.redact(rawContext);
        String contextJson = repository.toJson(context);
        if (contextJson.length() > 30000) {
            throw new BizException(422, "AI 上下文超过大小限制，请缩短输入或当前计划内容");
        }

        String suggestionId = repository.newSuggestionId();
        String inputHash = sha256(contextJson);
        AiProvider provider = providerRegistry.require(config.providerCode());
        AiProvider.ProviderConfig providerConfig = new AiProvider.ProviderConfig(config.providerCode(),
                config.baseUrl(), cryptoService.decrypt(config.apiKeyCiphertext()), config.modelName(), config.timeoutSeconds());
        long started = System.currentTimeMillis();
        int inputTokens = 0;
        int outputTokens = 0;
        try {
            String effectiveUserPrompt = promptDecorator.apply(prompt.userTemplate());
            AiProvider.ProviderResponse first = provider.complete(providerConfig,
                    new AiProvider.ProviderRequest(sceneCode, prompt.systemPrompt(), effectiveUserPrompt, context));
            inputTokens += first.inputTokens();
            outputTokens += first.outputTokens();
            T result;
            try {
                result = validator.apply(first.content(), suggestionId);
            } catch (AiOutputValidator.OutputException firstError) {
                String repairPrompt = effectiveUserPrompt + "\n上次输出未通过校验：" + firstError.getMessage()
                        + "。请重新生成完整 JSON，不要只返回被指出的字段；仅返回符合上述契约的 JSON。";
                AiProvider.ProviderResponse repaired = provider.complete(providerConfig,
                        new AiProvider.ProviderRequest(sceneCode, prompt.systemPrompt(), repairPrompt, context));
                inputTokens += repaired.inputTokens();
                outputTokens += repaired.outputTokens();
                result = validator.apply(repaired.content(), suggestionId);
            }
            String outputJson = repository.toJson(result);
            repository.saveSuccess(callRecord(user, requestId, sceneCode, suggestionId, bizType, bizId,
                    inputHash, contextJson, config, prompt, outputJson, inputTokens, outputTokens, started));
            return result;
        } catch (RuntimeException ex) {
            String errorCode = ex instanceof BizException biz ? "HTTP_" + biz.getCode()
                    : ex instanceof AiOutputValidator.OutputException ? "OUTPUT_INVALID" : "AI_CALL_FAILED";
            if (ex instanceof AiOutputValidator.OutputException) {
                log.warn("[AI输出校验] 两次输出均未通过 scene={}, suggestionId={}, reason={}",
                        sceneCode, suggestionId, ex.getMessage());
            }
            repository.saveFailure(callRecord(user, requestId, sceneCode, suggestionId, bizType, bizId,
                    inputHash, contextJson, config, prompt, null, inputTokens, outputTokens, started),
                    errorCode, ex.getMessage());
            if (ex instanceof AiOutputValidator.OutputException) {
                throw new BizException(502, "AI 返回内容不符合计划格式，请重试");
            }
            throw ex;
        }
    }

    private AiRepository.CallRecord callRecord(AuthUser user, String requestId, String sceneCode,
                                                 String suggestionId, String bizType, Long bizId,
                                                 String inputHash, String contextJson,
                                                 AiRepository.ModelConfig config,
                                                 AiRepository.PromptTemplate prompt,
                                                 String outputJson, int inputTokens, int outputTokens, long started) {
        String inputSummary = contextJson.length() <= 10000 ? contextJson : contextJson.substring(0, 10000);
        return new AiRepository.CallRecord(suggestionId, requestId, sceneCode, user.userId(), user.deptId(),
                bizType, bizId, inputHash, inputSummary, config.providerCode(), config.modelName(),
                prompt.versionNo(), outputJson, inputTokens, outputTokens, System.currentTimeMillis() - started);
    }

    private void requireAvailable(AiRepository.ModelConfig config, AuthUser user, String sceneCode) {
        if (!isAvailable(config, user, sceneCode)) {
            throw new BizException(403, "AI 功能未开启或当前员工不在灰度范围内");
        }
    }

    private boolean isAvailable(AiRepository.ModelConfig config, AuthUser user, String sceneCode) {
        if (!config.globalEnabled() || !config.sceneEnabled(sceneCode)) return false;
        boolean hasUserAllowList = StringUtils.hasText(config.allowedUserIds());
        boolean hasOrgAllowList = StringUtils.hasText(config.allowedOrgIds());
        if (!hasUserAllowList && !hasOrgAllowList) return true;
        return containsId(config.allowedUserIds(), user.userId()) || containsId(config.allowedOrgIds(), user.deptId());
    }

    private boolean containsId(String values, Long id) {
        if (!StringUtils.hasText(values) || id == null) return false;
        String expected = String.valueOf(id);
        for (String value : values.split(",")) if (value.trim().equals(expected)) return true;
        return false;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BizException(500, "AI 输入摘要计算失败");
        }
    }
}
