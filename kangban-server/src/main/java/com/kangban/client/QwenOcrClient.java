package com.kangban.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Qwen OCR client for images and PDF pages. */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "qwen")
public class QwenOcrClient implements OcrClient {

    private static final int MAX_PDF_PAGES = 10;
    private static final int PDF_RENDER_DPI = 180;

    private final AiConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public QwenOcrClient(AiConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeout()))
                .proxy(DirectProxySelector.INSTANCE)
                .build();
    }

    @Override
    public OcrResult analyze(Long taskId, String fileUrl, String fileType) {
        long start = System.currentTimeMillis();
        log.info("Qwen OCR started: taskId={}, fileType={}", taskId, fileType);
        try {
            byte[] fileBytes = fetchFile(fileUrl);
            OcrResult result = isPdf(fileType, fileUrl)
                    ? analyzePdf(taskId, fileBytes)
                    : analyzeImage(taskId, fileBytes, imageMimeType(fileType));

            if (!result.hasText()) {
                throw new OcrClientException("OCR 未返回可用文本");
            }
            log.info("Qwen OCR completed: taskId={}, elapsed={}ms", taskId, System.currentTimeMillis() - start);
            return result;
        } catch (OcrClientException e) {
            log.warn("Qwen OCR rejected: taskId={}, elapsed={}ms", taskId, System.currentTimeMillis() - start);
            throw e;
        } catch (Exception e) {
            log.error("Qwen OCR failed: taskId={}, elapsed={}ms, errorType={}",
                    taskId, System.currentTimeMillis() - start, e.getClass().getSimpleName());
            throw new OcrClientException("OCR 服务处理失败");
        }
    }

    private OcrResult analyzePdf(Long taskId, byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new OcrClientException("PDF 不包含可识别页面");
            }
            if (pageCount > MAX_PDF_PAGES) {
                throw new OcrClientException("PDF 页数超过当前识别上限");
            }

            PDFRenderer renderer = new PDFRenderer(document);
            List<String> pageTexts = new ArrayList<>();
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, PDF_RENDER_DPI);
                try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", output);
                    OcrResult pageResult = analyzeImage(taskId, output.toByteArray(), "image/png");
                    pageTexts.add("第 " + (pageIndex + 1) + " 页\n" + pageResult.ocrText());
                }
            }
            return new OcrResult(String.join("\n\n", pageTexts), "", "", "", 0.0);
        }
    }

    private OcrResult analyzeImage(Long taskId, byte[] imageBytes, String mimeType) throws Exception {
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        Map<String, Object> body = Map.of(
                "model", config.getOcrModel(),
                "messages", List.of(
                        Map.of("role", "user", "content", List.of(
                                Map.of("type", "text", "text", "逐字提取图中的中文、数字和表格文字。"
                                        + "仅返回 JSON：{\"ocr_text\":\"提取的原始文本\"}。"
                                        + "无法辨认的内容请标注为[待确认]；不要推断诊断、检查结论或医疗建议。"),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                        ))
                ),
                "temperature", 0.0,
                "max_tokens", 4096
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.getApiUrl()))
                .header("Authorization", "Bearer " + config.getApiKey())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(config.getReadTimeout()))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new OcrClientException("OCR 服务暂不可用");
        }

        JsonNode choices = objectMapper.readTree(response.body()).path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new OcrClientException("OCR 响应格式无效");
        }
        String content = choices.get(0).path("message").path("content").asText("").trim();
        if (content.isBlank()) {
            throw new OcrClientException("OCR 未返回文本");
        }

        String cleaned = content.replace("```json", "").replace("```", "").trim();
        try {
            String text = objectMapper.readTree(cleaned).path("ocr_text").asText("").trim();
            if (!text.isBlank()) {
                return new OcrResult(text, "", "", "", 0.0);
            }
        } catch (Exception ignored) {
            // Some compatible endpoints return plain OCR text despite the JSON instruction.
        }
        return new OcrResult(content, "", "", "", 0.0);
    }

    private byte[] fetchFile(String fileUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fileUrl))
                .timeout(Duration.ofMillis(config.getReadTimeout()))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length == 0) {
            throw new OcrClientException("无法读取病历文件");
        }
        return response.body();
    }

    private boolean isPdf(String fileType, String fileUrl) {
        return "application/pdf".equalsIgnoreCase(fileType)
                || (fileUrl != null && fileUrl.toLowerCase().contains(".pdf"));
    }

    private String imageMimeType(String fileType) {
        return fileType != null && fileType.startsWith("image/") ? fileType : "image/jpeg";
    }
}
