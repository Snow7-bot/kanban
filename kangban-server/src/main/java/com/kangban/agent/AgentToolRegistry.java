package com.kangban.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 工具白名单。
 *
 * <p>只有被 Spring 注册且通过只读校验的工具才能被执行。重复名称或写工具
 * 会在启动时直接失败，避免工具定义被静默覆盖。</p>
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> tools;

    public AgentToolRegistry(List<AgentTool> registeredTools) {
        Map<String, AgentTool> entries = new LinkedHashMap<>();
        for (AgentTool tool : registeredTools == null ? List.<AgentTool>of() : registeredTools) {
            if (tool == null || tool.name() == null || tool.name().isBlank()) {
                throw new IllegalStateException("Agent 工具名称不能为空");
            }
            if (!tool.readOnly()) {
                throw new IllegalStateException("当前阶段只允许注册只读 Agent 工具: " + tool.name());
            }
            if (entries.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Agent 工具名称重复: " + tool.name());
            }
        }
        this.tools = Map.copyOf(entries);
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    public boolean contains(String name) {
        return tools.containsKey(name);
    }

    public Collection<AgentTool> all() {
        return List.copyOf(tools.values());
    }

    public List<String> names() {
        return new ArrayList<>(tools.keySet());
    }
}
