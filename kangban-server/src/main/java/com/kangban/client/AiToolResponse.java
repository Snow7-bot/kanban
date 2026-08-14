package com.kangban.client;

import java.util.List;

/** 模型一次响应：要么返回最终文本，要么请求受控工具。 */
public record AiToolResponse(String content, List<AiToolCall> toolCalls) {

    public AiToolResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
