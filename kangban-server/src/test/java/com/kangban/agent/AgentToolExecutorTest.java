package com.kangban.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AgentToolExecutorTest {

    @Test
    void executesRegisteredReadOnlyToolWithServerContext() {
        AgentTool tool = new AgentTool() {
            @Override
            public String name() {
                return "get_patient_snapshot";
            }

            @Override
            public String description() {
                return "读取当前授权患者快照";
            }

            @Override
            public AgentToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
                return AgentToolResult.success(name(), context.scopeKey() + ":" + arguments.get("range"));
            }
        };
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(List.of(tool)));
        AgentExecutionContext context = context(9L, 15L, 2L, 31L, 60);

        AgentToolResult result = executor.execute(context,
                new AgentToolCall("get_patient_snapshot", Map.of("range", "30d", "subjectUserId", 999L)));

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.SUCCESS);
        assertThat(result.toolName()).isEqualTo("get_patient_snapshot");
        assertThat(result.content()).isEqualTo("15:2:30d");
    }

    @Test
    void rejectsExpiredContextBeforeCallingTool() {
        AgentTool tool = mock(AgentTool.class);
        org.mockito.Mockito.when(tool.name()).thenReturn("get_patient_snapshot");
        org.mockito.Mockito.when(tool.readOnly()).thenReturn(true);
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(List.of(tool)));
        long now = Instant.now().getEpochSecond();
        AgentExecutionContext context = new AgentExecutionContext(
                9L, 15L, 2L, 31L, "run-expired", "trace-expired", now - 10, now - 1);
        org.mockito.Mockito.clearInvocations(tool);

        AgentToolResult result = executor.execute(context, new AgentToolCall("get_patient_snapshot", Map.of()));

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.BLOCKED);
        assertThat(result.errorCode()).isEqualTo("CONTEXT_EXPIRED");
        org.mockito.Mockito.verifyNoInteractions(tool);
    }

    @Test
    void returnsSafeFailuresForUnknownAndThrowingTools() {
        AgentTool throwingTool = new AgentTool() {
            @Override
            public String name() {
                return "get_patient_snapshot";
            }

            @Override
            public String description() {
                return "测试工具";
            }

            @Override
            public AgentToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
                throw new IllegalStateException("database details must not escape");
            }
        };
        AgentToolExecutor executor = new AgentToolExecutor(new AgentToolRegistry(List.of(throwingTool)));
        AgentExecutionContext context = context(9L, 9L, null, 31L, 60);

        AgentToolResult unknown = executor.execute(context, new AgentToolCall("delete_patient_data", Map.of()));
        AgentToolResult failed = executor.execute(context, new AgentToolCall("get_patient_snapshot", Map.of()));

        assertThat(unknown.errorCode()).isEqualTo("UNKNOWN_TOOL");
        assertThat(unknown.content()).doesNotContain("database");
        assertThat(failed.errorCode()).isEqualTo("TOOL_EXECUTION_FAILED");
        assertThat(failed.content()).doesNotContain("database");
    }

    @Test
    void registryRejectsDuplicateAndWriteTools() {
        AgentTool first = tool("same_name", true);
        AgentTool duplicate = tool("same_name", true);
        AgentTool writeTool = tool("write_health_record", false);

        assertThatThrownBy(() -> new AgentToolRegistry(List.of(first, duplicate)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("名称重复");
        assertThatThrownBy(() -> new AgentToolRegistry(List.of(writeTool)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("只读");
    }

    private AgentTool tool(String name, boolean readOnly) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name;
            }

            @Override
            public boolean readOnly() {
                return readOnly;
            }

            @Override
            public AgentToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
                return AgentToolResult.success(name, "ok");
            }
        };
    }

    private AgentExecutionContext context(Long actor, Long subject, Long member, Long session, long ttl) {
        long now = Instant.now().getEpochSecond();
        return new AgentExecutionContext(actor, subject, member, session,
                "run-test", "trace-test", now - 1, now + ttl);
    }
}
