package com.planning.platform.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "planning.ai")
public class AiProperties {

    private boolean enabled;
    private String provider = "qwen";
    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
    private String model = "qwen3.7-plus";
    private String promptVersion = "v10-calibrated-grounded";
    private boolean thinkingEnabled;
    private int maxOutputTokens = 1600;
    private int requestTimeoutSeconds = 60;
    private int maxEvidenceCharacters = 30000;

    public boolean available() {
        return enabled && StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl) && StringUtils.hasText(model);
    }

    @PostConstruct
    void logMode() {
        if (available()) {
            log.info("AI environment fallback configured: provider={}, model={}, promptVersion={}, thinkingEnabled={}; "
                            + "system-managed database configuration remains authoritative when present",
                    provider, model, promptVersion, thinkingEnabled);
            return;
        }
        log.info("AI environment fallback not configured: AI_ENABLED={}, apiKeyConfigured={}; "
                        + "system-managed database configuration will be used when available",
                enabled, StringUtils.hasText(apiKey));
    }
}
