package com.kangban.agent;

/** 不包含患者正文的工具执行轨迹，供后续同步/SSE 输出使用。 */
public record AgentToolTrace(
        String toolName,
        AgentToolResult.Status status,
        int iteration,
        long elapsedMs,
        String errorCode
) {

    public AgentToolTrace {
        toolName = toolName == null ? "" : toolName;
        status = status == null ? AgentToolResult.Status.FAILED : status;
        iteration = Math.max(1, iteration);
        elapsedMs = Math.max(0, elapsedMs);
        errorCode = errorCode == null ? "" : errorCode;
    }
}
