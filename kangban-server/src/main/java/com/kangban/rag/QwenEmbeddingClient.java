package com.kangban.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.RagProperties;
import com.kangban.agent.AgentMetrics;
import com.kangban.client.AiConfig;
import com.kangban.client.DirectProxySelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qwen 中文文本向量客户端。
 *
 * <p>仅在 embedding-provider=qwen 时创建；它与聊天模型共用 API key，但使用独立的
 * Embeddings endpoint。远程失败会抛出 RAG 不可用异常，不静默切回哈希向量。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.agent.rag.embedding-provider", havingValue = "qwen")
public class QwenEmbeddingClient implements EmbeddingClient {

    private final RagProperties properties;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AgentMetrics metrics;

    @org.springframework.beans.factory.annotation.Autowired
    public QwenEmbeddingClient(RagProperties properties,
                               AiConfig aiConfig,
                               ObjectMapper objectMapper,
                               AgentMetrics metrics) {
        this.properties = properties;
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(aiConfig.getConnectTimeout()))
                .proxy(DirectProxySelector.INSTANCE)
                .build();
    }

    public QwenEmbeddingClient(RagProperties properties,
                               AiConfig aiConfig,
                               ObjectMapper objectMapper) {
        this(properties, aiConfig, objectMapper, new AgentMetrics());
    }

    @Override
    public double[] embed(String text) {
        List<double[]> vectors = embedBatch(List.of(text == null ? "" : text));
        return vectors.get(0);
    }

    @Override
    public List<double[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            throw unavailable("中文向量服务暂未配置，请联系管理员。");
        }
        int batchSize = Math.max(1, Math.min(32, properties.getEmbeddingBatchSize()));
        List<double[]> result = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += batchSize) {
            int end = Math.min(texts.size(), start + batchSize);
            result.addAll(embedBatchOnce(texts.subList(start, end)));
        }
        return List.copyOf(result);
    }

    @Override
    public int dimensions() {
        return properties.getEmbeddingDimensions();
    }

    private List<double[]> embedBatchOnce(List<String> texts) {
        long startedAt = System.currentTimeMillis();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getEmbeddingModel());
            body.put("input", texts.stream().map(text -> text == null ? "" : text).toList());
            body.put("dimensions", properties.getEmbeddingDimensions());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getEmbeddingApiUrl()))
                    .header("Authorization", "Bearer " + aiConfig.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(aiConfig.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Qwen embedding failed: status={}, batchSize={}, elapsed={}ms",
                        response.statusCode(), texts.size(), System.currentTimeMillis() - startedAt);
                if (response.statusCode() == 408 || response.statusCode() == 504) {
                    throw unavailable("中文向量服务响应超时，请稍后重试。");
                }
                if (response.statusCode() == 429) {
                    throw unavailable("中文向量服务当前请求较多，请稍后重试。");
                }
                throw unavailable("中文向量服务暂时不可用，请稍后重试。");
            }
            List<double[]> vectors = parse(objectMapper.readTree(response.body()), texts.size());
            metrics.recordEmbedding("qwen", texts.size(), System.currentTimeMillis() - startedAt, "success");
            return vectors;
        } catch (RagUnavailableException e) {
            metrics.recordEmbedding("qwen", texts.size(), System.currentTimeMillis() - startedAt, "failure");
            throw e;
        } catch (HttpTimeoutException e) {
            metrics.recordEmbedding("qwen", texts.size(), System.currentTimeMillis() - startedAt, "timeout");
            log.warn("Qwen embedding timeout: batchSize={}, elapsed={}ms", texts.size(),
                    System.currentTimeMillis() - startedAt);
            throw unavailable("中文向量服务响应超时，请稍后重试。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            metrics.recordEmbedding("qwen", texts.size(), System.currentTimeMillis() - startedAt, "interrupted");
            throw unavailable("中文向量服务请求被中断，请稍后重试。");
        } catch (Exception e) {
            metrics.recordEmbedding("qwen", texts.size(), System.currentTimeMillis() - startedAt, "failure");
            log.warn("Qwen embedding error: batchSize={}, elapsed={}ms, errorType={}",
                    texts.size(), System.currentTimeMillis() - startedAt, e.getClass().getSimpleName());
            throw unavailable("中文向量服务暂时不可用，请稍后重试。");
        }
    }

    private List<double[]> parse(JsonNode root, int expectedSize) {
        JsonNode data = root.path("data");
        if (!data.isArray() || data.size() != expectedSize) {
            throw unavailable("中文向量服务返回结果数量异常，请稍后重试。");
        }
        List<JsonNode> entries = new ArrayList<>();
        data.forEach(entries::add);
        entries.sort(Comparator.comparingInt(node -> node.path("index").asInt(Integer.MAX_VALUE)));

        List<double[]> vectors = new ArrayList<>(entries.size());
        for (JsonNode entry : entries) {
            JsonNode values = entry.path("embedding");
            if (!values.isArray() || values.size() != dimensions()) {
                throw unavailable("中文向量维度与配置不一致，请重建索引或检查配置。");
            }
            double[] vector = new double[values.size()];
            double norm = 0.0;
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).asDouble();
                if (!Double.isFinite(vector[i])) {
                    throw unavailable("中文向量服务返回了无效向量，请稍后重试。");
                }
                norm += vector[i] * vector[i];
            }
            normalize(vector, norm);
            vectors.add(vector);
        }
        return vectors;
    }

    private void normalize(double[] vector, double normSquared) {
        double norm = Math.sqrt(normSquared);
        if (norm == 0.0) {
            throw unavailable("中文向量服务返回了空向量，请稍后重试。");
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    private RagUnavailableException unavailable(String message) {
        return new RagUnavailableException(message);
    }
}
