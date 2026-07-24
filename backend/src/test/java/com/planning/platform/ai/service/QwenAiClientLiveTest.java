package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.config.AiProperties;
import com.planning.platform.ai.model.AiReviewModels.AnalysisRequest;
import com.planning.platform.ai.model.AiReviewModels.ModelAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "QWEN_LIVE_TEST", matches = "true")
class QwenAiClientLiveTest {

    @Test
    void returnsGroundedDimensionsForSyntheticPlan() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey(System.getenv("QWEN_API_KEY"));
        properties.setModel("qwen3.7-plus");
        properties.setThinkingEnabled(false);
        properties.setMaxOutputTokens(3000);
        properties.setRequestTimeoutSeconds(45);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AiGroundingService groundingService = new AiGroundingService();
        QwenAiClient client = new QwenAiClient(properties, objectMapper);
        AnalysisRequest request = groundingService.prepare("MONTH_PLAN", List.of(
                Map.of("id", "SEM_PLAN_01", "text", "任务内容应具体说明行动、对象和预期结果，不能只有笼统表述。"),
                Map.of("id", "SEM_PLAN_02", "text", "任务目标应包含可观察结果、数量、范围或明确通过条件。")
        ), Map.of(
                "plan", Map.of("planMonth", "2099-01", "summary", "提升平台质量"),
                "items", List.of(Map.of(
                        "taskName", "系统优化",
                        "taskContent", "优化系统",
                        "deliverable", "优化结果"
                )),
                "instruction", "检查虚构计划的具体性和可衡量性。"
        ));

        ModelAnalysis raw = client.analyze(request);
        ModelAnalysis validated = groundingService.validate(request, raw);

        assertThat(raw.analysisDimensions()).hasSize(2);
        assertThat(validated.analysisDimensions()).hasSize(2);
        assertThat(validated.analysisDimensions())
                .allSatisfy(item -> assertThat(item.status()).isIn("PASS", "RISK", "UNKNOWN"));
        assertThat(validated.analysisDimensions())
                .anySatisfy(item -> assertThat(item.status()).isEqualTo("RISK"));
        assertThat(validated.issues()).isNotEmpty();
    }
}
