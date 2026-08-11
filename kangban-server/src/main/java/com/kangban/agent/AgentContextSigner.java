package com.kangban.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 为内部 Agent 调用准备的短期上下文签名器。
 *
 * <p>它不是用户 JWT，也不承载长期登录凭证；密钥为空时禁止签发。</p>
 */
@Component
@RequiredArgsConstructor
public class AgentContextSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String VERSION = "v1";

    private final ObjectMapper objectMapper;
    private final AgentProperties properties;

    public String sign(AgentExecutionContext context) {
        String secret = requireSecret();
        try {
            String payload = encode(objectMapper.writeValueAsBytes(context));
            String unsigned = VERSION + "." + payload;
            return unsigned + "." + encode(hmac(unsigned, secret));
        } catch (Exception e) {
            throw new IllegalStateException("Agent 上下文签名失败", e);
        }
    }

    public AgentExecutionContext verify(String token) {
        String secret = requireSecret();
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Agent 上下文签名无效");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Agent 上下文签名无效");
        }
        String unsigned = parts[0] + "." + parts[1];
        byte[] expected = hmac(unsigned, secret);
        byte[] provided;
        try {
            provided = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Agent 上下文签名无效");
        }
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new IllegalArgumentException("Agent 上下文签名无效");
        }
        try {
            AgentExecutionContext context = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), AgentExecutionContext.class);
            if (context.expiredAt(System.currentTimeMillis() / 1000)) {
                throw new IllegalArgumentException("Agent 上下文已过期");
            }
            return context;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Agent 上下文签名无效");
        }
    }

    private String requireSecret() {
        if (properties.getContextSecret() == null || properties.getContextSecret().isBlank()) {
            throw new IllegalStateException("未配置 Agent 上下文签名密钥");
        }
        return properties.getContextSecret();
    }

    private byte[] hmac(String content, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Agent 上下文签名失败", e);
        }
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
