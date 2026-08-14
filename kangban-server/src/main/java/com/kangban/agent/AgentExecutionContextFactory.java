package com.kangban.agent;

import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * 仅从服务端已校验的身份和患者范围创建 Agent 上下文。
 */
@Component
@RequiredArgsConstructor
public class AgentExecutionContextFactory {

    private final AgentProperties properties;

    public AgentExecutionContext create(Long actorUserId, Long subjectUserId,
                                        Long memberId, Long sessionId) {
        if (actorUserId == null || subjectUserId == null || sessionId == null) {
            throw new IllegalArgumentException("Agent 身份和会话范围不能为空");
        }
        long issuedAt = Instant.now().getEpochSecond();
        long ttl = Math.max(30, properties.getContextTtlSeconds());
        String runId = UUID.randomUUID().toString();
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            traceId = runId;
        }
        return new AgentExecutionContext(
                actorUserId,
                subjectUserId,
                memberId,
                sessionId,
                runId,
                traceId,
                issuedAt,
                issuedAt + ttl
        );
    }
}
