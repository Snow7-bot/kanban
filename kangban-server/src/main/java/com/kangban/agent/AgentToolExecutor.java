package com.kangban.agent;

import com.kangban.client.AiToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 工具统一执行边界。
 *
 * <p>身份、患者和会话范围来自服务端上下文；过期、未知或非只读工具不会触达业务服务。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentToolExecutor {

    private final AgentToolRegistry registry;

    public List<AiToolDefinition> definitions() {
        return registry.all().stream()
                .map(tool -> new AiToolDefinition(tool.name(), tool.description(), tool.inputSchema()))
                .toList();
    }

    public AgentToolResult execute(AgentExecutionContext context, AgentToolCall call) {
        if (context == null) {
            return AgentToolResult.failed(toolName(call), "INVALID_CONTEXT", "Agent 上下文无效。");
        }
        if (context.expiredAt(java.time.Instant.now().getEpochSecond())) {
            return AgentToolResult.blocked(toolName(call), "CONTEXT_EXPIRED", "本次问诊上下文已失效，请重新发送。");
        }
        if (call == null || call.name() == null || call.name().isBlank()) {
            return AgentToolResult.failed("", "INVALID_TOOL_CALL", "工具名称不能为空。");
        }

        AgentTool tool = registry.get(call.name());
        if (tool == null) {
            return AgentToolResult.failed(call.name(), "UNKNOWN_TOOL", "未找到可用的医疗工具。");
        }
        if (!tool.readOnly()) {
            return AgentToolResult.blocked(tool.name(), "WRITE_TOOL_NOT_ALLOWED", "当前 Agent 不允许执行写入操作。");
        }

        try {
            AgentToolResult result = tool.execute(context, call.arguments());
            if (result == null) {
                return AgentToolResult.failed(tool.name(), "EMPTY_TOOL_RESULT", "工具未返回有效结果。");
            }
            return result.withToolName(tool.name());
        } catch (RuntimeException e) {
            log.warn("Agent tool failed: tool={}, runId={}, errorType={}",
                    tool.name(), context.runId(), e.getClass().getSimpleName());
            return AgentToolResult.failed(tool.name(), "TOOL_EXECUTION_FAILED", "医疗数据工具暂时不可用，请稍后重试。");
        }
    }

    private String toolName(AgentToolCall call) {
        return call == null || call.name() == null ? "" : call.name();
    }
}
