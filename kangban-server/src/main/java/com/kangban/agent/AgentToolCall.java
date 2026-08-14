package com.kangban.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一次 Agent 工具调用请求；调用方不能通过参数覆盖授权上下文。 */
public record AgentToolCall(String name, Map<String, Object> arguments) {

    public AgentToolCall {
        arguments = arguments == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(arguments));
    }
}
