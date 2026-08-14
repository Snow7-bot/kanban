package com.kangban.client;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发送给模型的工具白名单定义。
 *
 * <p>参数 schema 只描述允许的输入形状，不包含用户身份；身份范围始终由服务端上下文决定。</p>
 */
public record AiToolDefinition(
        String name,
        String description,
        Map<String, Object> parameters
) {

    public AiToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("AI 工具名称不能为空");
        }
        description = description == null ? "" : description;
        parameters = parameters == null
                ? Map.of("type", "object", "properties", Map.of())
                : Map.copyOf(new LinkedHashMap<>(parameters));
    }
}
