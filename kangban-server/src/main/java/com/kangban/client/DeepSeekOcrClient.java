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
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek OCR client — uses DeepSeek Vision API for medical document analysis.
 * Activated when app.ai.provider=deepseek.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "deepseek")
public class DeepSeekOcrClient implements OcrClient {

    private final AiConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeepSeekOcrClient(AiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .build();
    }

    @Override
    public OcrResult analyze(Long taskId, String fileUrl, String fileType) {
        long start = System.currentTimeMillis();
        log.info("DeepSeek OCR analyze: taskId={}, fileType={}", taskId, fileType);
        try {
            // Fetch image bytes from URL
            byte[] imageBytes = fetchImage(fileUrl);

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = fileType != null ? fileType : "image/jpeg";
            String dataUri = "data:" + mimeType + ";base64," + base64Image;

            Map<String, Object> body = Map.of(
                    "model", config.getOcrModel(),
                    "messages", List.of(
                            Map.of("role", "user", "content", List.of(
                                    Map.of("type", "text", "text", "请分析这张医学影像/病历文件，提取以下信息并以JSON格式返回：\n"
                                            + "{\n"
                                            + "  \"ocr_text\": \"原始文本内容\",\n"
                                            + "  \"diagnosis_conclusion\": \"诊断结论\",\n"
                                            + "  \"findings\": \"检查所见\",\n"
                                            + "  \"advice\": \"建议\",\n"
                                            + "  \"confidence\": 0.95\n"
                                            + "}"),
                                    Map.of("type", "image_url", "image_url",
                                            Map.of("url", dataUri))
                            ))
                    ),
                    "temperature", 0.3,
                    "max_tokens", 2048
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
                log.error("DeepSeek OCR error: status={}, taskId={}, elapsed={}ms", response.statusCode(), taskId, elapsed);
                return OcrResult.empty();
            }

            JsonNode json = objectMapper.readTree(response.body());
            String content = json.path("choices").get(0).path("message").path("content").asText();

            // Try to parse JSON from the response
            String cleaned = content.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode result;
            try {
                result = objectMapper.readTree(cleaned);
            } catch (Exception e) {
                log.warn("OCR response not valid JSON, using raw text: taskId={}", taskId);
                return new OcrResult(content, "", "", "", 0.5);
            }

            log.info("DeepSeek OCR done: taskId={}, elapsed={}ms", taskId, elapsed);
            return new OcrResult(
                    result.path("ocr_text").asText(""),
                    result.path("diagnosis_conclusion").asText(""),
                    result.path("findings").asText(""),
                    result.path("advice").asText(""),
                    result.path("confidence").asDouble(0.8)
            );

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("DeepSeek OCR failed: taskId={}, elapsed={}ms, error={}", taskId, elapsed, e.getMessage());
            return OcrResult.empty();
        }
    }

    private byte[] fetchImage(String fileUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .timeout(Duration.ofMillis(config.getReadTimeout()))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        return response.body();
    }
}
