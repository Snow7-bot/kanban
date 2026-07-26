package com.kangban.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI client configuration — loaded from environment variables.
 * Sensitive values (api-key) default to empty; must be set in environment.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.ai")
public class AiConfig {

    /** Provider: mock | qwen | deepseek */
    private String provider = "mock";

    /** OpenAI-compatible API base URL */
    private String apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    /** API key — NEVER commit real value */
    private String apiKey = "";

    /** Model for AI consultation */
    private String aiModel = "qwen3.7-plus-2026-05-26";

    /** Model for OCR (vision-capable) */
    private String ocrModel = "qwen3.5-ocr";

    /** Connect timeout in ms */
    private long connectTimeout = 10_000;

    /** Read timeout in ms */
    private long readTimeout = 60_000;
}
