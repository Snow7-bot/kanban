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
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI consultation client — calls DeepSeek Chat API.
 * Activated when app.ai.provider=deepseek.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "deepseek")
public class DeepSeekAiConsultationClient implements AiConsultationClient {

    private final AiConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekAiConsultationClient(AiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .build();
    }

    @Override
    public String consult(Long sessionId, String userContent, String patientData) {
        long start = System.currentTimeMillis();
        log.info("DeepSeek consult: sessionId={}", sessionId);
        try {
            String systemPrompt = buildSystemPrompt(patientData);

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
                log.error("DeepSeek API error: status={}, sessionId={}, elapsed={}ms", response.statusCode(), sessionId, elapsed);
                return "[AI服务暂时不可用] 状态码: " + response.statusCode() + "，请稍后重试。";
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").get(0).path("message").path("content").asText();
            log.info("DeepSeek consult done: sessionId={}, elapsed={}ms", sessionId, elapsed);
            return content;

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("DeepSeek consult failed: sessionId={}, elapsed={}ms, error={}", sessionId, elapsed, e.getMessage());
            return "[AI服务连接失败] " + e.getMessage() + "，请稍后重试。";
        }
    }

    private String buildSystemPrompt(String patientData) {
        StringBuilder sb = new StringBuilder("你是一个专业的智能医疗助手（康伴）。请根据用户描述的症状提供初步分析和建议。");
        sb.append(" 注意：你的建议仅供参考，不能替代专业医疗诊断。");
        if (patientData != null && !patientData.isBlank() && !"{}".equals(patientData)) {
            sb.append(" 患者信息：").append(patientData);
        }
        return sb.toString();
    }
}
