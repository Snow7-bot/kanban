package com.kangban.agent;

import java.util.Map;

/**
 * 康伴 Agent 的受控工具协议。
 *
 * <p>当前阶段只允许只读工具。工具必须从服务端创建的
 * {@link AgentExecutionContext} 获取身份和患者范围，不能信任 arguments
 * 中的 userId、subjectUserId 或 memberId。</p>
 */
public interface AgentTool {

    String name();

    String description();

    default boolean readOnly() {
        return true;
    }

    /** 模型可见的 JSON Schema；不允许声明身份字段。 */
    default Map<String, Object> inputSchema() {
        return Map.of("type", "object", "properties", Map.of());
    }

    AgentToolResult execute(AgentExecutionContext context, Map<String, Object> arguments);
}
