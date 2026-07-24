package com.planning.platform.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planning.platform.ai.config.AiProperties;
import com.planning.platform.ai.model.AiReviewModels.AnalysisRequest;
import com.planning.platform.ai.model.AiReviewModels.SourceReference;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QwenAiClientTest {

    @Test
    void buildsBoundedNonThinkingRequestForPlanReview() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setModel("qwen3.7-plus");
        properties.setThinkingEnabled(false);
        properties.setMaxOutputTokens(3000);
        QwenAiClient client = new QwenAiClient(properties, new ObjectMapper());
        AnalysisRequest request = new AnalysisRequest(
                "DAY_PLAN",
                List.of(Map.of("id", "SEM_DAY_01", "text", "内容应具体")),
                Map.of(
                        "dayPlan", Map.of("content", "联系13812345678优化系统"),
                        "acceptanceCriteria", List.of(Map.of("id", "AC_1", "text", "联系13812345678验收"))
                ),
                List.of(new SourceReference("SRC_0001", "businessData.dayPlan.content",
                        "附件13812345678.pdf", "联系13812345678优化系统")),
                Map.of("SEM_DAY_01", List.of("SRC_0001"))
        );

        Map<String, Object> payload = client.buildPayload(request);

        assertThat(payload.get("model")).isEqualTo("qwen3.7-plus");
        assertThat(payload.get("enable_thinking")).isEqualTo(false);
        assertThat(payload.get("max_tokens")).isEqualTo(1600);
        assertThat(payload.get("response_format")).isEqualTo(Map.of("type", "json_object"));
        assertThat(payload.get("messages")).asList().hasSize(2);
        assertThat(String.valueOf(((List<?>) payload.get("messages")).get(0)))
                .contains("analysisDimensions", "逐条处理 rules", "PASS|RISK|UNKNOWN", "requiredReferences");

        @SuppressWarnings("unchecked")
        Map<String, Object> userMessage = (Map<String, Object>) ((List<?>) payload.get("messages")).get(1);
        var modelInput = new ObjectMapper().readTree(String.valueOf(userMessage.get("content")));
        assertThat(modelInput.has("businessData")).isFalse();
        assertThat(modelInput.path("sourceCatalog")).hasSize(1);
        assertThat(modelInput.path("sourceCatalog").get(0).path("content").asText())
                .isEqualTo("联系138****5678优化系统");
        assertThat(modelInput.path("sourceCatalog").get(0).path("label").asText())
                .isEqualTo("附件138****5678.pdf");
        assertThat(modelInput.path("requiredReferences").path("SEM_DAY_01").get(0).asText())
                .isEqualTo("SRC_0001");
        assertThat(modelInput.path("acceptanceCriteria").get(0).path("text").asText())
                .isEqualTo("联系138****5678验收");
    }

    @Test
    void enforcesMinimumOutputBudget() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setMaxOutputTokens(20);
        QwenAiClient client = new QwenAiClient(properties, new ObjectMapper());
        AnalysisRequest request = new AnalysisRequest("DAY_PLAN", List.of(), Map.of(), List.of());

        assertThat(client.buildPayload(request).get("max_tokens")).isEqualTo(256);
    }

    @Test
    void enforcesDatabaseUserAndOrganizationAllowLists() {
        AiProperties properties = new AiProperties();
        AiRepository repository = mock(AiRepository.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        AiRepository.ModelConfig config = new AiRepository.ModelConfig(
                1L, "千问检查", "ALIYUN", "https://example.com/v1", "cipher",
                "qwen3.7-plus", 30, true, true, true, true,
                "4", "110", 10, 30, 20, 1, "ENABLED");
        when(repository.activeConfig()).thenReturn(Optional.of(config));
        when(cryptoService.decrypt("cipher")).thenReturn("key");
        QwenAiClient client = new QwenAiClient(properties, new ObjectMapper(), repository,
                cryptoService, new AiRedactionService());

        assertThat(client.permitted(4L, 999L)).isTrue();
        assertThat(client.permitted(5L, 110L)).isTrue();
        assertThat(client.permitted(5L, 999L)).isFalse();
        assertThat(client.checkDailyLimit()).isEqualTo(20);
    }

    @Test
    void activeDatabaseConfigCannotBeBypassedByEnvironmentFallback() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey("environment-key");
        properties.setBaseUrl("https://environment.example.com/v1");
        properties.setModel("environment-model");
        AiRepository repository = mock(AiRepository.class);
        AiCryptoService cryptoService = mock(AiCryptoService.class);
        AiRepository.ModelConfig disabled = new AiRepository.ModelConfig(
                1L, "管理员配置", "ALIYUN", "https://database.example.com/v1", "cipher",
                "qwen3.7-plus", 30, false, true, true, true,
                "", "", 10, 30, 20, 2, "ENABLED");
        when(repository.activeConfig()).thenReturn(Optional.of(disabled));
        QwenAiClient client = new QwenAiClient(properties, new ObjectMapper(), repository,
                cryptoService, new AiRedactionService());

        assertThat(client.available()).isFalse();
        assertThat(client.model()).isEqualTo("qwen3.7-plus");
    }

    @Test
    void retriesOneTransientProviderFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
                return;
            }
            byte[] body = successfulResponse().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            QwenAiClient client = liveClient(server.getAddress().getPort());

            var result = client.analyze(sampleRequest());

            assertThat(calls).hasValue(2);
            assertThat(result.analysisDimensions()).singleElement()
                    .satisfies(item -> assertThat(item.status()).isEqualTo("PASS"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotRetryInvalidModelCredentials() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();
        try {
            QwenAiClient client = liveClient(server.getAddress().getPort());

            assertThatThrownBy(() -> client.analyze(sampleRequest()))
                    .hasMessageContaining("配置验证失败");
            assertThat(calls).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsOversizedSourceCatalogBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            QwenAiClient client = liveClient(server.getAddress().getPort());
            String oversized = "任".repeat(40001);
            AnalysisRequest request = new AnalysisRequest(
                    "DAY_PLAN", List.of(Map.of("id", "SEM_DAY_01", "text", "内容应具体")),
                    Map.of(), List.of(new SourceReference("SRC_0001", "businessData.dayPlan.content",
                    "当前日计划 · 任务内容", oversized)));

            assertThatThrownBy(() -> client.analyze(request))
                    .hasMessageContaining("内容过长");
            assertThat(calls).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    private QwenAiClient liveClient(int port) {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://127.0.0.1:" + port + "/v1/chat/completions");
        properties.setModel("qwen3.7-plus");
        properties.setRequestTimeoutSeconds(10);
        return new QwenAiClient(properties, new ObjectMapper());
    }

    private AnalysisRequest sampleRequest() {
        return new AnalysisRequest(
                "DAY_PLAN",
                List.of(Map.of("id", "SEM_DAY_01", "text", "内容应具体")),
                Map.of("dayPlan", Map.of("content", "完成接口联调")),
                List.of(new SourceReference("SRC_0001", "businessData.dayPlan.content",
                        "当前日计划 · 任务内容", "完成接口联调"))
        );
    }

    private String successfulResponse() {
        return """
                {"choices":[{"message":{"content":"{\\"summary\\":\\"检查完成\\",\\"analysisDimensions\\":[{\\"ruleId\\":\\"SEM_DAY_01\\",\\"title\\":\\"任务具体性\\",\\"status\\":\\"PASS\\",\\"conclusion\\":\\"内容具体\\",\\"quote\\":\\"完成接口联调\\",\\"basis\\":\\"包含明确行动\\",\\"confidence\\":0.9,\\"references\\":[\\"SRC_0001\\"]}],\\"issues\\":[],\\"acceptanceCoverage\\":[]}"}}],"usage":{"prompt_tokens":120,"completion_tokens":80}}
                """;
    }
}
