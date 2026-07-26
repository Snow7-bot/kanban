package com.kangban.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Qwen (DashScope) AI consultation client.
 * Activated when app.ai.provider=qwen.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "qwen")
public class QwenAiConsultationClient implements AiConsultationClient {

    private final AiConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenAiConsultationClient(AiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .proxy(DirectProxySelector.INSTANCE)
                .build();
    }

    @Override
    public String consult(Long sessionId, String userContent, String patientData) {
        long start = System.currentTimeMillis();
        log.info("Qwen consult: sessionId={}", sessionId);
        if (config.getApiKey() == null || config.getApiKey().isBlank()) {
            throw new AiClientException("AI 服务暂未配置，请联系管理员。");
        }
        try {
            String systemPrompt = "你是康伴的个性化健康信息辅助助手。后端会提供当前选中患者的授权数据库快照。"
                    + "只能基于该患者的快照和本次对话回答，禁止推断或混用其他家庭成员的数据。"
                    + "仅提供一般健康信息，不做诊断、不开具处方、不替代医生。"
                    + "数据不足时明确说明，不得编造；出现胸痛、呼吸困难、意识异常、大出血等紧急症状时，建议立即线下急诊。";
            if (patientData != null && !patientData.isBlank() && !"{}".equals(patientData)) {
                systemPrompt += " 当前患者数据库快照：" + patientData;
            }

            Map<String, Object> body = Map.of(
                    "model", config.getAiModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userContent)
                    ),
                    "temperature", 0.7,
                    "max_tokens", 1024
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiUrl()))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(config.getReadTimeout()))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            if (response.statusCode() != 200) {
                log.error("Qwen API error: status={}, sessionId={}, elapsed={}ms", response.statusCode(), sessionId, elapsed);
                if (response.statusCode() == 429) {
                    throw new AiClientException("当前请求较多，请稍后重试。");
                }
                if (response.statusCode() == 408 || response.statusCode() == 504) {
                    throw new AiClientException("AI 响应超时，请重试。");
                }
                throw new AiClientException("AI 服务暂时不可用，请稍后重试。");
            }

            JsonNode json = objectMapper.readTree(response.body());
            JsonNode choices = json.path("choices");
            String content = choices.isArray() && !choices.isEmpty()
                    ? choices.get(0).path("message").path("content").asText()
                    : "";
            if (content.isBlank()) {
                throw new AiClientException("AI 服务暂未返回有效内容，请稍后重试。");
            }
            log.info("Qwen consult done: sessionId={}, elapsed={}ms", sessionId, elapsed);
            return content;

        } catch (AiClientException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Qwen consult timeout: sessionId={}, elapsed={}ms", sessionId, elapsed);
            throw new AiClientException("AI 响应超时，请重试。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Qwen consult interrupted: sessionId={}", sessionId);
            throw new AiClientException("AI 服务暂时不可用，请稍后重试。");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Qwen consult failed: sessionId={}, elapsed={}ms, errorType={}",
                    sessionId, elapsed, e.getClass().getSimpleName());
            throw new AiClientException("AI 服务暂时不可用，请稍后重试。");
        }
    }
}
