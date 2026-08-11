package com.kangban.agent;

import java.util.Objects;

/**
 * 一次 Agent 运行的最小授权上下文。
 *
 * <p>该对象由服务端根据已完成的家庭权限校验创建，客户端不能直接构造或覆盖。</p>
 */
public record AgentExecutionContext(
        Long actorUserId,
        Long subjectUserId,
        Long memberId,
        Long sessionId,
        String runId,
        String traceId,
        long issuedAtEpochSecond,
        long expiresAtEpochSecond
) {

    public AgentExecutionContext {
        Objects.requireNonNull(actorUserId, "actorUserId");
        Objects.requireNonNull(subjectUserId, "subjectUserId");
        Objects.requireNonNull(sessionId, "sessionId");
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId 不能为空");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId 不能为空");
        }
        if (expiresAtEpochSecond <= issuedAtEpochSecond) {
            throw new IllegalArgumentException("Agent 上下文有效期无效");
        }
    }

    public boolean expiredAt(long epochSecond) {
        return epochSecond >= expiresAtEpochSecond;
    }

    /** 只用于日志和审计关联，不包含患者正文。 */
    public String scopeKey() {
        return subjectUserId + ":" + (memberId == null ? "all" : memberId);
    }
}
