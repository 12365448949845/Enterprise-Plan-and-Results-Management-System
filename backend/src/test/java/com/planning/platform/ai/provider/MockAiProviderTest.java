package com.planning.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.planning.platform.ai.model.AiModels;
import com.planning.platform.ai.service.AiOutputValidator;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MockAiProvider provider = new MockAiProvider(objectMapper);

    @Test
    void generatesStructuredDraftForLocalDevelopment() throws Exception {
        ObjectNode context = objectMapper.createObjectNode()
                .put("planMonth", YearMonth.now().plusMonths(1).toString())
                .put("intentText", "完成客户方案；推进产品上线；沉淀复盘模板");

        AiProvider.ProviderResponse response = provider.complete(
                new AiProvider.ProviderConfig("MOCK", "", "", "planning-mock-v1", 30),
                new AiProvider.ProviderRequest(AiModels.MONTH_PLAN_DRAFT, "system", "user", context));
        JsonNode output = objectMapper.readTree(response.content());

        assertThat(output.path("items").size()).isEqualTo(3);
        assertThat(output.path("items").get(0).has("completionRate")).isFalse();
        assertThat(output.path("items").get(0).has("estimatedHours")).isFalse();
        assertThat(response.inputTokens()).isPositive();
    }

    @Test
    void structuredOutputPassesValidationForOneHundredRepresentativeInputs() {
        AiOutputValidator validator = new AiOutputValidator(objectMapper);
        YearMonth month = YearMonth.now().plusMonths(1);
        int valid = 0;
        for (int index = 0; index < 100; index++) {
            ObjectNode context = objectMapper.createObjectNode()
                    .put("planMonth", month.toString())
                    .put("intentText", "Task " + index + "; Deliver result " + index + "; Review evidence " + index);
            AiProvider.ProviderResponse response = provider.complete(
                    new AiProvider.ProviderConfig("MOCK", "", "", "planning-mock-v1", 30),
                    new AiProvider.ProviderRequest(AiModels.MONTH_PLAN_DRAFT, "system", "user", context));
            validator.validateGenerate(response.content(), "AI-" + index, month.toString(), java.util.List.of());
            valid++;
        }
        assertThat(valid).isEqualTo(100);
    }
}
