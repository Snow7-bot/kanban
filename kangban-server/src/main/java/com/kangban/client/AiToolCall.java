package com.kangban.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 模型返回的一次工具调用。 */
public record AiToolCall(String id, String name, Map<String, Object> arguments) {

    public AiToolCall {
        id = id == null ? "" : id;
        name = name == null ? "" : name;
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}
