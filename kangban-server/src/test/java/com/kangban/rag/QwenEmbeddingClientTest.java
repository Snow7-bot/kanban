package com.kangban.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.RagProperties;
import com.kangban.client.AiConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QwenEmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    private HttpServer server;
    private int responseStatus;

    @BeforeEach
    void setUp() throws IOException {
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsChineseEmbeddingBatchAndNormalizesReturnedVectors() {
        RagProperties properties = properties(3, 8);
        QwenEmbeddingClient client = new QwenEmbeddingClient(properties, aiConfig(), objectMapper);

        List<double[]> vectors = client.embedBatch(List.of("血压管理", "用药时间"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).containsExactly(1.0, 0.0, 0.0);
        assertThat(vectors.get(1)[0]).isEqualTo(0.0);
        assertThat(vectors.get(1)[1]).isEqualTo(0.6, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(vectors.get(1)[2]).isEqualTo(0.8, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(requestBody.get().path("model").asText()).isEqualTo("text-embedding-v4");
        assertThat(requestBody.get().path("dimensions").asInt()).isEqualTo(3);
        assertThat(requestBody.get().path("input")).hasSize(2);
    }

    @Test
    void splitsLargeBatchByConfiguredBatchSize() {
        RagProperties properties = properties(3, 1);
        QwenEmbeddingClient client = new QwenEmbeddingClient(properties, aiConfig(), objectMapper);

        List<double[]> vectors = client.embedBatch(List.of("一", "二", "三"));

        assertThat(vectors).hasSize(3);
        assertThat(requestBody.get().path("input")).hasSize(1);
    }

    @Test
    void doesNotSilentlyFallBackWhenEmbeddingServiceFails() {
        responseStatus = 503;
        QwenEmbeddingClient client = new QwenEmbeddingClient(properties(3, 8), aiConfig(), objectMapper);

        assertThatThrownBy(() -> client.embed("血压管理"))
                .isInstanceOf(RagUnavailableException.class)
                .hasMessage("中文向量服务暂时不可用，请稍后重试。");
    }

    private RagProperties properties(int dimensions, int batchSize) {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingApiUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/embeddings");
        properties.setEmbeddingModel("text-embedding-v4");
        properties.setEmbeddingDimensions(dimensions);
        properties.setEmbeddingBatchSize(batchSize);
        return properties;
    }

    private AiConfig aiConfig() {
        AiConfig config = new AiConfig();
        config.setApiKey("YOUR_TEST_ONLY_API_KEY");
        config.setConnectTimeout(3000);
        config.setReadTimeout(3000);
        return config;
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestBody.set(objectMapper.readTree(exchange.getRequestBody()));
        String response = "{\"data\":["
                + "{\"index\":1,\"embedding\":[0,3,4]},"
                + "{\"index\":0,\"embedding\":[2,0,0]}"
                + "]}";
        if (requestBody.get().path("input").size() == 1) {
            response = "{\"data\":[{\"index\":0,\"embedding\":[2,0,0]}]}";
        }
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
