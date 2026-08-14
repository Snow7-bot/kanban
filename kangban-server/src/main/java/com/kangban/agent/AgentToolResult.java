package com.kangban.agent;

/** 工具执行结果；content 只供本轮 Agent 编排使用，不写入日志。 */
public record AgentToolResult(
        String toolName,
        Status status,
        String content,
        String errorCode
) {

    public enum Status {
        SUCCESS,
        BLOCKED,
        FAILED
    }

    public AgentToolResult {
        toolName = toolName == null ? "" : toolName;
        status = status == null ? Status.FAILED : status;
        content = content == null ? "" : content;
        errorCode = errorCode == null ? "" : errorCode;
    }

    public static AgentToolResult success(String toolName, String content) {
        return new AgentToolResult(toolName, Status.SUCCESS, content, "");
    }

    public static AgentToolResult blocked(String toolName, String errorCode, String message) {
        return new AgentToolResult(toolName, Status.BLOCKED, message, errorCode);
    }

    public static AgentToolResult failed(String toolName, String errorCode, String message) {
        return new AgentToolResult(toolName, Status.FAILED, message, errorCode);
    }

    public AgentToolResult withToolName(String name) {
        return new AgentToolResult(name, status, content, errorCode);
    }
}
