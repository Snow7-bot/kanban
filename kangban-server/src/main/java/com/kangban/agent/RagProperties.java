package com.kangban.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置边界。第一阶段只建立契约和开关，公共资料入库与生产向量库在后续阶段启用。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.agent.rag")
public class RagProperties {

    private boolean enabled = false;
    private String vectorStore = "memory";
    private int topK = 5;
    private double minScore = 0.7;
    private int maxContextTokens = 6000;
}
