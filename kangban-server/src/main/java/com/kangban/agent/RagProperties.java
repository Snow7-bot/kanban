package com.kangban.agent;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 配置边界。公共知识库的管理接口由独立令牌保护，令牌只从环境变量绑定。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.agent.rag")
public class RagProperties {

    private boolean enabled = false;
    /** RAG 开启后是否同时检索当前授权患者的家庭私有病历。 */
    private boolean privateEnabled = true;
    private String vectorStore = "memory";
    private int topK = 5;
    private double minScore = 0.7;
    private int maxContextTokens = 6000;
    private String adminToken = "";
    /** local 仅用于测试/离线；qwen 调用真实中文向量服务。 */
    private String embeddingProvider = "local";
    private String embeddingApiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings";
    private String embeddingModel = "local-hash-v1";
    private int embeddingDimensions = 1024;
    private int embeddingBatchSize = 16;
    private long maxFileBytes = 10 * 1024 * 1024L;
}
