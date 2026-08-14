package com.kangban.rag;

import com.kangban.agent.RagProperties;
import com.kangban.common.BusinessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class RagAdminGuard {

    private final RagProperties properties;

    public RagAdminGuard(RagProperties properties) {
        this.properties = properties;
    }

    public void require(String providedToken) {
        String configuredToken = properties.getAdminToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            throw BusinessException.forbidden("知识库管理凭据尚未配置");
        }
        if (providedToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8))) {
            throw BusinessException.forbidden("知识库管理凭据无效");
        }
    }
}
