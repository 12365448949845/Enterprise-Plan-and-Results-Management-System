package com.planning.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;

public interface AiProvider {

    boolean supports(String providerCode);

    ProviderResponse complete(ProviderConfig config, ProviderRequest request);

    record ProviderConfig(
            String providerCode,
            String baseUrl,
            String apiKey,
            String modelName,
            int timeoutSeconds
    ) {
    }

    record ProviderRequest(
            String sceneCode,
            String systemPrompt,
            String userPrompt,
            JsonNode context
    ) {
    }

    record ProviderResponse(String content, int inputTokens, int outputTokens) {
    }
}
