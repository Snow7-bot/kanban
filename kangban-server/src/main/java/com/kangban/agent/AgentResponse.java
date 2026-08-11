package com.kangban.agent;

import java.util.List;

/**
 * Agent 编排结果。引用和动作使用结构化字段，避免后续再次解析自然语言。
 */
public record AgentResponse(
        String content,
        String runId,
        List<Citation> citations,
        List<ActionProposal> actions
) {

    public AgentResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
