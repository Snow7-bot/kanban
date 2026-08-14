package com.kangban.client;

import java.util.List;

/** 工具循环中回传给模型的 assistant/tool 消息。 */
public record AiToolMessage(
        String role,
        String content,
        String toolCallId,
        List<AiToolCall> toolCalls
) {

    public AiToolMessage {
        role = role == null ? "" : role;
        content = content == null ? "" : content;
        toolCallId = toolCallId == null ? "" : toolCallId;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static AiToolMessage assistant(String content, List<AiToolCall> toolCalls) {
        return new AiToolMessage("assistant", content, "", toolCalls);
    }

    public static AiToolMessage tool(String toolCallId, String content) {
        return new AiToolMessage("tool", content, toolCallId, List.of());
    }
}
