package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.platform.ai.model.AiAdminModels;
import com.planning.platform.ai.provider.AiProvider;
import com.planning.platform.ai.provider.AiProviderRegistry;
import com.planning.platform.common.exception.BizException;
import com.planning.platform.common.security.AuthUser;
import com.planning.platform.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiManagementService {

    private final AiRepository repository;
    private final AiCryptoService cryptoService;
    private final AiProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public List<AiAdminModels.ModelConfigResponse> configs() {
        return repository.configResponses();
    }

    @Transactional
    public AiAdminModels.ModelConfigResponse createConfig(AuthUser user, AiAdminModels.SaveModelConfigRequest request) {
        String providerCode = validateProvider(request);
        String encryptedKey = StringUtils.hasText(request.apiKey())
                ? cryptoService.encrypt(request.apiKey().trim()) : null;
        requireApiKey(providerCode, encryptedKey);
        AiRepository.ModelConfig saved = repository.createConfig(user, request, encryptedKey);
        auditLogService.success(user, "AI_MODEL_CONFIG_CREATE", "AI_MODEL_CONFIG", saved.id(),
                configAuditJson(saved));
        return saved.toResponse();
    }

    @Transactional
    public AiAdminModels.ModelConfigResponse updateConfig(Long id, AuthUser user,
                                                          AiAdminModels.SaveModelConfigRequest request) {
        if (request.versionNo() == null) throw new BizException(422, "配置版本不能为空");
        String providerCode = validateProvider(request);
        AiRepository.ModelConfig current = repository.requireModelConfig(id);
        String encryptedKey = current.apiKeyCiphertext();
        if (StringUtils.hasText(request.apiKey())) encryptedKey = cryptoService.encrypt(request.apiKey().trim());
        requireApiKey(providerCode, encryptedKey);
        AiRepository.ModelConfig saved = repository.updateConfig(id, user, request, encryptedKey);
        auditLogService.success(user, "AI_MODEL_CONFIG_SAVE", "AI_MODEL_CONFIG", saved.id(),
                configAuditJson(saved));
        return saved.toResponse();
    }

    @Transactional
    public AiAdminModels.ModelConfigResponse enableConfig(Long id, AuthUser user) {
        AiRepository.ModelConfig previous = repository.requireActiveConfig();
        AiRepository.ModelConfig enabled = repository.enableConfig(id, user);
        auditLogService.success(user, "AI_MODEL_CONFIG_ENABLE", "AI_MODEL_CONFIG", enabled.id(),
                repository.toJson(Map.of("previousConfigId", previous.id(), "enabledConfigId", enabled.id(),
                        "providerCode", enabled.providerCode(), "modelName", enabled.modelName())));
        return enabled.toResponse();
    }

    public AiAdminModels.TestConnectionResponse testConnection(Long id) {
        AiRepository.ModelConfig config = repository.requireModelConfig(id);
        log.info("[AI连接测试] 开始 provider={}, model={}, baseUrl={}", config.providerCode(), config.modelName(), config.baseUrl());
        AiProvider provider = providerRegistry.require(config.providerCode());
        ObjectNode context = objectMapper.createObjectNode()
                .put("test", true)
                .put("notice", "这是连接测试，不包含真实员工数据");
        long started = System.currentTimeMillis();
        try {
            AiProvider.ProviderResponse response = provider.complete(
                    new AiProvider.ProviderConfig(config.providerCode(), config.baseUrl(),
                            cryptoService.decrypt(config.apiKeyCiphertext()), config.modelName(), config.timeoutSeconds()),
                    new AiProvider.ProviderRequest("CONNECTION_TEST",
                            "你是连接测试助手，只返回 JSON：{\"status\":\"ok\"}",
                            "请返回连接测试结果，不处理任何业务数据。", context));
            long latency = System.currentTimeMillis() - started;
            if (!StringUtils.hasText(response.content())) {
                log.error("[AI连接测试] 失败：返回内容为空, latency={}ms", latency);
                throw new BizException(502, "模型连接测试未返回内容");
            }
            log.info("[AI连接测试] 成功 latency={}ms", latency);
            return new AiAdminModels.TestConnectionResponse(true, config.providerCode(), config.modelName(), latency, "连接成功");
        } catch (BizException ex) {
            long latency = System.currentTimeMillis() - started;
            log.error("[AI连接测试] 失败 latency={}ms, error={}", latency, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            long latency = System.currentTimeMillis() - started;
            log.error("[AI连接测试] 未预期异常 latency={}ms", latency, ex);
            throw new BizException(500, "连接测试失败: " + ex.getMessage());
        }
    }

    public List<AiAdminModels.PromptResponse> prompts() {
        return repository.prompts();
    }

    @Transactional
    public AiAdminModels.PromptResponse publishPrompt(AuthUser user, AiAdminModels.PublishPromptRequest request) {
        AiAdminModels.PromptResponse result = repository.publishPrompt(user, request);
        auditLogService.success(user, "AI_PROMPT_PUBLISH", "AI_PROMPT", result.id(),
                repository.toJson(Map.of("sceneCode", result.sceneCode(), "versionNo", result.versionNo())));
        return result;
    }

    public AiAdminModels.MetricsResponse metrics(int days) {
        return repository.metrics(days);
    }

    private String validateProvider(AiAdminModels.SaveModelConfigRequest request) {
        String providerCode = request.providerCode().toUpperCase(Locale.ROOT);
        if (!List.of("MOCK", "OPENAI_COMPATIBLE", "ALIYUN").contains(providerCode)) {
            throw new BizException(422, "模型供应商只支持 MOCK、OPENAI_COMPATIBLE 或 ALIYUN");
        }
        if (!"MOCK".equals(providerCode)) validateBaseUrl(request.baseUrl());
        return providerCode;
    }

    private void requireApiKey(String providerCode, String encryptedKey) {
        if (!"MOCK".equals(providerCode) && !StringUtils.hasText(encryptedKey)) {
            throw new BizException(422, "非 Mock 模型必须配置 API Key");
        }
    }

    private String configAuditJson(AiRepository.ModelConfig config) {
        return repository.toJson(Map.of("providerCode", config.providerCode(), "modelName", config.modelName(),
                "globalEnabled", config.globalEnabled(), "versionNo", config.versionNo()));
    }

    private void validateBaseUrl(String value) {
        if (!StringUtils.hasText(value)) throw new BizException(422, "模型调用地址不能为空");
        try {
            URI uri = URI.create(value.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BizException(422, "模型调用地址必须为合法的 HTTP/HTTPS 地址");
        }
    }
}
