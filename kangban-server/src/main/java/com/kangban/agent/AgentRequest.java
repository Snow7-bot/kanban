package com.kangban.agent;

/**
 * 内置 Agent 的一次请求。患者上下文必须由后端从授权数据构造。
 */
public record AgentRequest(
        AgentExecutionContext context,
        String message,
        String patientContextJson
) {

    public AgentRequest {
        if (context == null) {
            throw new IllegalArgumentException("Agent 上下文不能为空");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("用户问题不能为空");
        }
    }
}
