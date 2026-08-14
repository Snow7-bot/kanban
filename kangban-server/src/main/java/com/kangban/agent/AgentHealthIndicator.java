package com.kangban.agent;

import com.kangban.client.AiConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Agent/RAG 配置健康检查，不输出 API key、签名密钥或业务正文。
 */
@Component("kangbanAgent")
@RequiredArgsConstructor
public class AgentHealthIndicator implements HealthIndicator {

    private final AgentProperties agentProperties;
    private final RagProperties ragProperties;
    private final AiConfig aiConfig;

    @Override
    public Health health() {
        String provider = aiConfig.getProvider() == null ? "unknown" : aiConfig.getProvider();
        boolean providerConfigured = "mock".equalsIgnoreCase(provider)
                || (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isBlank());
        Health.Builder builder;
        if (!agentProperties.isEnabled()) {
            builder = Health.outOfService();
        } else if (!providerConfigured) {
            builder = Health.down();
        } else {
            builder = Health.up();
        }
        return builder
                .withDetail("enabled", agentProperties.isEnabled())
                .withDetail("provider", provider)
                .withDetail("providerConfigured", providerConfigured)
                .withDetail("ragEnabled", ragProperties.isEnabled())
                .withDetail("vectorStore", ragProperties.getVectorStore())
                .build();
    }
}
