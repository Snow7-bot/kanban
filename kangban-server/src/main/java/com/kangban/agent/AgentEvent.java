package com.kangban.agent;

import java.util.Map;

/**
 * 同步和 SSE 共享的 Agent 事件类型。
 */
public record AgentEvent(
        Type type,
        String runId,
        Map<String, Object> data
) {

    public enum Type {
        THINKING,
        TOKEN,
        CITATION,
        ACTION,
        WARNING,
        DONE,
        ERROR
    }
}
