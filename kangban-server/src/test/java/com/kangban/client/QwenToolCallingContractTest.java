package com.kangban.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.ConversationMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QwenToolCallingContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private AtomicReference<JsonNode> requestBody;

    @BeforeEach
    void setUp() throws IOException {
        requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsWhitelistedToolsAndParsesModelToolCall() throws Exception {
        QwenAiConsultationClient client = client();
        AiToolResponse response = client.consultWithTools(
                31L,
                "最近血压怎么样",
                "{}",
                List.of(new AiToolDefinition("get_health_metrics", "读取健康指标", Map.of(
                        "type", "object",
                        "properties", Map.of("metric", Map.of("type", "string")),
                        "additionalProperties", false
                ))),
                List.of());

        assertThat(client.supportsToolCalling()).isTrue();
        assertThat(response.content()).isEmpty();
        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.id()).isEqualTo("call-1");
            assertThat(call.name()).isEqualTo("get_health_metrics");
            assertThat(call.arguments()).containsEntry("metric", "blood_pressure");
        });
        JsonNode body = requestBody.get();
        assertThat(body.path("tool_choice").asText()).isEqualTo("auto");
        assertThat(body.path("tools")).hasSize(1);
        assertThat(body.path("tools").path(0).path("function").path("name").asText())
                .isEqualTo("get_health_metrics");
        assertThat(body.path("messages").path(1).path("content").asText())
                .isEqualTo("最近血压怎么样");
    }

    @Test
    void serializesAssistantAndToolHistoryForNextIteration() throws Exception {
        QwenAiConsultationClient client = client();
        client.consultWithTools(
                31L,
                "最近血压怎么样",
                "{}",
                List.of(new AiToolDefinition("get_health_metrics", "读取健康指标", Map.of("type", "object"))),
                List.of(
                        AiToolMessage.assistant("", List.of(new AiToolCall(
                                "call-1", "get_health_metrics", Map.of("metric", "blood_pressure")))),
                        AiToolMessage.tool("call-1", "{\"value\":\"128/80\"}")));

        JsonNode messages = requestBody.get().path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.path(2).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.path(2).path("tool_calls").path(0).path("id").asText())
                .isEqualTo("call-1");
        assertThat(messages.path(3).path("role").asText()).isEqualTo("tool");
        assertThat(messages.path(3).path("tool_call_id").asText()).isEqualTo("call-1");
    }

    @Test
    void placesConversationHistoryBeforeCurrentQuestionAndKeepsToolHistorySeparate() throws Exception {
        QwenAiConsultationClient client = client();
        client.consultWithTools(
                31L,
                "那最近一次呢",
                "{}",
                List.of(new AiToolDefinition("get_health_metrics", "读取健康指标", Map.of("type", "object"))),
                List.of(),
                List.of(
                        new ConversationMessage("user", "我上次的血压是多少"),
                        new ConversationMessage("assistant", "上次记录是 128/80。")));

        JsonNode messages = requestBody.get().path("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.path(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.path(1).path("content").asText()).contains("上次的血压");
        assertThat(messages.path(2).path("role").asText()).isEqualTo("assistant");
        assertThat(messages.path(3).path("content").asText()).isEqualTo("那最近一次呢");
    }

    private QwenAiConsultationClient client() {
        AiConfig config = new AiConfig();
        config.setApiKey("test-only-key");
        config.setApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/chat");
        config.setReadTimeout(3000);
        return new QwenAiConsultationClient(config, objectMapper);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
        byte[] body = "{\"choices\":[{\"message\":{\"content\":\"\",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"get_health_metrics\",\"arguments\":\"{\\\"metric\\\":\\\"blood_pressure\\\"}\"}}]}}]}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
