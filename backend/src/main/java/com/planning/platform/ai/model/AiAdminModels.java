package com.planning.platform.ai.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class AiAdminModels {

    private AiAdminModels() {
    }

    public record ModelConfigResponse(
            Long id,
            String configName,
            String providerCode,
            String baseUrl,
            boolean apiKeyConfigured,
            String modelName,
            Integer timeoutSeconds,
            boolean globalEnabled,
            boolean draftEnabled,
            boolean optimizeEnabled,
            boolean checkEnabled,
            String allowedUserIds,
            String allowedOrgIds,
            Integer draftDailyLimit,
            Integer optimizeDailyLimit,
            Integer checkDailyLimit,
            Integer versionNo,
            String status
    ) {
    }

    public record SaveModelConfigRequest(
            @NotBlank @Size(max = 120) String configName,
            @NotBlank String providerCode,
            @Size(max = 500) String baseUrl,
            @Size(max = 2000) String apiKey,
            @NotBlank @Size(max = 120) String modelName,
            @NotNull @Min(5) @Max(120) Integer timeoutSeconds,
            boolean globalEnabled,
            boolean draftEnabled,
            boolean optimizeEnabled,
            boolean checkEnabled,
            @Size(max = 2000) String allowedUserIds,
            @Size(max = 2000) String allowedOrgIds,
            @NotNull @Min(1) @Max(1000) Integer draftDailyLimit,
            @NotNull @Min(1) @Max(5000) Integer optimizeDailyLimit,
            @NotNull @Min(1) @Max(5000) Integer checkDailyLimit,
            Integer versionNo
    ) {
    }

    public record PromptResponse(
            Long id,
            String sceneCode,
            String versionNo,
            String systemPrompt,
            String userTemplate,
            String outputSchemaVersion,
            String status,
            String createdAt
    ) {
    }

    public record PublishPromptRequest(
            @NotBlank String sceneCode,
            @NotBlank @Size(max = 20000) String systemPrompt,
            @NotBlank @Size(max = 20000) String userTemplate,
            @NotBlank @Size(max = 30) String outputSchemaVersion
    ) {
    }

    public record TestConnectionResponse(boolean success, String providerCode, String modelName, long latencyMs, String message) {
    }

    public record MetricsResponse(
            long totalCalls,
            long successCalls,
            double successRate,
            long inputTokens,
            long outputTokens,
            long averageLatencyMs,
            long adoptedSuggestions,
            double adoptionRate,
            List<Map<String, Object>> byScene
    ) {
    }
}
