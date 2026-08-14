package com.kangban.agent;

import com.kangban.client.AiConsultationClient;
import com.kangban.client.AiToolCall;
import com.kangban.client.AiToolDefinition;
import com.kangban.client.AiToolResponse;
import com.kangban.rag.RagSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {

    @Test
    void reusesExistingAiClientThroughOneBuiltInEntryPoint() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        AgentProperties properties = new AgentProperties();
        AgentExecutionContextFactory factory = mock(AgentExecutionContextFactory.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(aiClient, properties, factory);
        long now = System.currentTimeMillis() / 1000;
        AgentExecutionContext context = new AgentExecutionContext(
                9L, 15L, 2L, 31L, "run-1", "trace-1", now - 1, now + 60);
        when(aiClient.consult(31L, "血压偏高怎么办", "{\"subject\":{}}"))
                .thenReturn("请先记录血压并咨询医生。");

        AgentResponse response = orchestrator.run(new AgentRequest(
                context, "血压偏高怎么办", "{\"subject\":{}}"));

        assertThat(response.content()).isEqualTo("请先记录血压并咨询医生。");
        assertThat(response.runId()).isEqualTo("run-1");
        verify(aiClient).consult(31L, "血压偏高怎么办", "{\"subject\":{}}");
    }

    @Test
    void rejectsExpiredContextBeforeCallingProvider() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        AgentProperties properties = new AgentProperties();
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, properties, mock(AgentExecutionContextFactory.class));
        long now = System.currentTimeMillis() / 1000;
        AgentExecutionContext context = new AgentExecutionContext(
                9L, 9L, null, 31L, "run-1", "trace-1", now - 2, now - 1);

        assertThatThrownBy(() -> orchestrator.run(new AgentRequest(context, "问题", "{}")))
                .isInstanceOf(com.kangban.client.AiClientException.class)
                .hasMessage("本次问诊上下文已失效，请重新发送。");
        verifyNoInteractions(aiClient);
    }

    @Test
    void injectsKnowledgeEvidenceAndReturnsCitationsWhenEnabled() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        AgentProperties properties = new AgentProperties();
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        com.kangban.agent.Citation citation = new com.kangban.agent.Citation(
                "12", "血压指南", "1", 2, "监测", "官方资料", "2026-08-12");
        var search = mock(com.kangban.rag.KnowledgeSearchService.class);
        when(search.search("血压怎么记录")).thenReturn(new RagSearchResult(
                "[资料1] 血压指南\n每天固定时间记录。",
                java.util.List.of(new com.kangban.rag.KnowledgeSearchHit(
                        "每天固定时间记录。", 1.0, citation))));
        when(aiClient.consult(org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.contains("已审核知识库证据"),
                org.mockito.ArgumentMatchers.eq("{}")))
                .thenReturn("建议按资料记录。[资料1]");
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, properties, mock(AgentExecutionContextFactory.class), ragProperties, search);
        long now = System.currentTimeMillis() / 1000;
        AgentExecutionContext context = new AgentExecutionContext(
                9L, 15L, 2L, 31L, "run-rag", "trace-rag", now - 1, now + 60);

        AgentResponse response = orchestrator.run(new AgentRequest(context, "血压怎么记录", "{}"));

        assertThat(response.citations()).containsExactly(citation);
        org.mockito.Mockito.verify(aiClient).consult(
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.contains("[资料1]"),
                org.mockito.ArgumentMatchers.eq("{}"));
    }

    @Test
    void exposesRetrievedEvidenceWhenModelIncorrectlyClaimsNoEvidence() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        AgentProperties properties = new AgentProperties();
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        Citation citation = new Citation("12", "RAG验收-蓝色药盒", "1", null,
                "RAG 功能测试章节", "系统测试资料", "2026-08-12");
        var search = mock(com.kangban.rag.KnowledgeSearchService.class);
        when(search.search("蓝色药盒每天什么时候使用？")).thenReturn(new RagSearchResult(
                "[资料1] RAG验收-蓝色药盒\n蓝色药盒每天 21:10 使用一次。",
                java.util.List.of(new com.kangban.rag.KnowledgeSearchHit(
                        "蓝色药盒每天 21:10 使用一次。", 1.0, citation))));
        when(aiClient.consult(eq(31L), contains("蓝色药盒每天 21:10"), eq("{}")))
                .thenReturn("暂无足够依据回答该问题。");
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, properties, mock(AgentExecutionContextFactory.class), ragProperties, search);

        AgentResponse response = orchestrator.run(new AgentRequest(
                context("run-evidence-fallback"), "蓝色药盒每天什么时候使用？", "{}"));

        assertThat(response.content()).contains("蓝色药盒每天 21:10 使用一次").contains("[资料1]")
                .doesNotContain("暂无足够依据");
        assertThat(response.citations()).containsExactly(citation);
    }

    @Test
    void doesNotCallProviderWhenEnabledKnowledgeBaseHasNoEvidence() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, new AgentProperties(), mock(AgentExecutionContextFactory.class), ragProperties,
                query -> RagSearchResult.empty());
        long now = System.currentTimeMillis() / 1000;
        AgentExecutionContext context = new AgentExecutionContext(
                9L, 15L, 2L, 31L, "run-empty", "trace-empty", now - 1, now + 60);

        assertThatThrownBy(() -> orchestrator.run(new AgentRequest(context, "没有资料的问题", "{}")))
                .isInstanceOf(com.kangban.client.AiClientException.class)
                .hasMessageContaining("没有足够依据");
        org.mockito.Mockito.verifyNoInteractions(aiClient);
    }

    @Test
    void continuesPatientDataQuestionWhenKnowledgeBaseHasNoEvidence() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.execute(any(), any())).thenReturn(AgentToolResult.success(
                "get_health_metrics", "{\"records\":[{\"metric\":\"blood_pressure\",\"value\":\"128/80\"}]}")
        );
        when(aiClient.consult(eq(31L), contains("没有匹配的公共知识库证据"), eq("{}")))
                .thenReturn("最近30天有 1 条血压记录，未发现足够数据判断长期趋势。");
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, new AgentProperties(), mock(AgentExecutionContextFactory.class), ragProperties,
                query -> RagSearchResult.empty(), (query, context) -> RagSearchResult.empty(), toolExecutor);

        AgentResponse response = orchestrator.run(new AgentRequest(
                context("run-patient-empty-rag"),
                "请根据当前患者最近30天的健康数据，概括健康变化，并列出可能需要关注的异常项；没有记录的指标请明确说明。",
                "{}"));

        assertThat(response.content()).contains("血压记录");
        assertThat(response.citations()).isEmpty();
        verify(toolExecutor).execute(eq(context("run-patient-empty-rag")), argThat(call ->
                "get_health_metrics".equals(call.name())));
        verify(aiClient).consult(eq(31L), contains("没有匹配的公共知识库证据"), eq("{}"));
    }

    @Test
    void executesRelevantDatabaseToolAndInjectsItsResultIntoProviderPrompt() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        AgentProperties properties = new AgentProperties();
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.execute(any(), any())).thenReturn(AgentToolResult.success(
                "get_health_metrics", "{\"records\":[{\"metric\":\"blood_pressure\",\"value\":\"128/80\"}]}")
        );
        when(aiClient.consult(eq(31L), contains("get_health_metrics"), eq("{}")))
                .thenReturn("已读取当前患者血压记录。");
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, properties, mock(AgentExecutionContextFactory.class), new RagProperties(),
                query -> RagSearchResult.empty(), (query, context) -> RagSearchResult.empty(), toolExecutor);
        AgentExecutionContext context = context("run-tool");

        AgentResponse response = orchestrator.run(new AgentRequest(context, "最近血压怎么样", "{}"));

        assertThat(response.content()).contains("血压");
        assertThat(response.toolTraces()).singleElement().satisfies(trace -> {
            assertThat(trace.toolName()).isEqualTo("get_health_metrics");
            assertThat(trace.status()).isEqualTo(AgentToolResult.Status.SUCCESS);
        });
        verify(toolExecutor).execute(eq(context), argThat(call ->
                "get_health_metrics".equals(call.name())
                        && "blood_pressure".equals(call.arguments().get("metric"))));
    }

    @Test
    void defaultsToDeterministicServerSideToolPlanningWhenProviderSupportsToolCalling() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        when(aiClient.supportsToolCalling()).thenReturn(true);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.execute(any(), any())).thenReturn(AgentToolResult.success(
                "get_health_metrics", "{\"records\":[{\"value\":\"128/80\"}]}"));
        when(aiClient.consult(eq(31L), contains("get_health_metrics"), eq("{}")))
                .thenReturn("已读取当前患者血压记录。 ");
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, new AgentProperties(), mock(AgentExecutionContextFactory.class), new RagProperties(),
                query -> RagSearchResult.empty(), (query, context) -> RagSearchResult.empty(), toolExecutor);

        orchestrator.run(new AgentRequest(context("run-deterministic-tool"), "最近血压怎么样", "{}"));

        verify(toolExecutor).execute(any(), argThat(call -> "get_health_metrics".equals(call.name())));
        verify(aiClient, never()).consultWithTools(anyLong(), anyString(), anyString(), anyList(), anyList());
        verify(aiClient).consult(eq(31L), contains("get_health_metrics"), eq("{}"));
    }

    @Test
    void refusesProviderCallWhenDatabaseToolFails() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.execute(any(), any())).thenReturn(AgentToolResult.failed(
                "get_health_metrics", "TOOL_EXECUTION_FAILED", "医疗数据工具暂时不可用，请稍后重试。"));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, new AgentProperties(), mock(AgentExecutionContextFactory.class), new RagProperties(),
                query -> RagSearchResult.empty(), (query, context) -> RagSearchResult.empty(), toolExecutor);

        assertThatThrownBy(() -> orchestrator.run(new AgentRequest(
                        context("run-failed-tool"), "最近血压怎么样", "{}")))
                .isInstanceOf(com.kangban.client.AiClientException.class)
                .hasMessageContaining("健康数据工具暂时不可用");
        verify(aiClient, never()).consult(anyLong(), anyString(), anyString());
        verify(aiClient, never()).consultWithTools(anyLong(), anyString(), anyString(), anyList(), anyList());
    }

    @Test
    void keepsToolContextWhenRagIsAlsoEnabled() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.execute(any(), any())).thenReturn(AgentToolResult.success(
                "get_patient_health_snapshot", "{\"subject\":{\"name\":\"王阿姨\"}}"));
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        when(aiClient.consult(eq(31L),
                argThat(message -> message.contains("王阿姨") && message.contains("已审核知识库证据")), eq("{}")))
                .thenReturn("已结合患者资料和知识库回答。");
        Citation citation = new Citation("doc-1", "血压指南", "1", 1,
                "监测", "官方资料", "2026-08-12");
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, new AgentProperties(), mock(AgentExecutionContextFactory.class), ragProperties,
                query -> new RagSearchResult("[资料1] 指南\n固定时间测量。", List.of(
                        new com.kangban.rag.KnowledgeSearchHit("固定时间测量。", 1.0, citation))),
                (query, context) -> RagSearchResult.empty(), toolExecutor);

        orchestrator.run(new AgentRequest(context("run-tool-rag"), "如何管理", "{}"));

        verify(aiClient).consult(eq(31L),
                argThat(message -> message.contains("王阿姨")
                        && message.contains("已审核知识库证据")
                        && message.contains("数据库没有某项记录")
                && message.contains("固定时间测量")), eq("{}"));
    }

    @Test
    void letsModelChooseWhitelistedToolThenUsesReturnedDataForFinalAnswer() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        when(aiClient.supportsToolCalling()).thenReturn(true);
        AgentProperties properties = new AgentProperties();
        properties.setModelToolCallingEnabled(true);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.definitions()).thenReturn(List.of(new AiToolDefinition(
                "get_health_metrics", "读取健康指标", Map.of(
                "type", "object",
                "properties", Map.of("metric", Map.of("type", "string"))
        ))));
        when(toolExecutor.execute(any(), argThat(call ->
                "get_health_metrics".equals(call.name())
                        && "blood_pressure".equals(call.arguments().get("metric")))))
                .thenReturn(AgentToolResult.success("get_health_metrics", "{\"records\":[{\"value\":\"128/80\"}]}}"));
        when(aiClient.consultWithTools(eq(31L), eq("最近血压怎么样"), eq("{}"), anyList(), anyList()))
                .thenReturn(new AiToolResponse("", List.of(new AiToolCall(
                        "call-1", "get_health_metrics", Map.of("metric", "blood_pressure")))))
                .thenReturn(new AiToolResponse("最近一次血压记录为 128/80。", List.of()));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, properties, mock(AgentExecutionContextFactory.class), new RagProperties(),
                query -> RagSearchResult.empty(), (query, context) -> RagSearchResult.empty(), toolExecutor);

        AgentResponse response = orchestrator.run(new AgentRequest(
                context("run-model-tool"), "最近血压怎么样", "{}"));

        assertThat(response.content()).contains("128/80");
        assertThat(response.toolTraces()).singleElement().satisfies(trace -> {
            assertThat(trace.toolName()).isEqualTo("get_health_metrics");
            assertThat(trace.iteration()).isEqualTo(1);
            assertThat(trace.status()).isEqualTo(AgentToolResult.Status.SUCCESS);
        });
        verify(aiClient, times(2)).consultWithTools(eq(31L), eq("最近血压怎么样"), eq("{}"), anyList(), anyList());
    }

    @Test
    void stopsModelLoopWhenToolFailsWithoutGeneratingAnAnswer() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        when(aiClient.supportsToolCalling()).thenReturn(true);
        AgentProperties properties = new AgentProperties();
        properties.setModelToolCallingEnabled(true);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.definitions()).thenReturn(List.of(new AiToolDefinition(
                "get_health_metrics", "读取健康指标", Map.of("type", "object"))));
        when(toolExecutor.execute(any(), any())).thenReturn(AgentToolResult.failed(
                "get_health_metrics", "TOOL_EXECUTION_FAILED", "医疗数据工具暂时不可用，请稍后重试。"));
        when(aiClient.consultWithTools(eq(31L), eq("最近血压怎么样"), eq("{}"), anyList(), anyList()))
                .thenReturn(new AiToolResponse("", List.of(new AiToolCall(
                        "call-1", "get_health_metrics", Map.of()))));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, properties, mock(AgentExecutionContextFactory.class), new RagProperties(),
                query -> RagSearchResult.empty(), (query, context) -> RagSearchResult.empty(), toolExecutor);

        assertThatThrownBy(() -> orchestrator.run(new AgentRequest(
                        context("run-model-tool-failed"), "最近血压怎么样", "{}")))
                .isInstanceOf(com.kangban.client.AiClientException.class)
                .hasMessageContaining("健康数据工具暂时不可用");
        verify(aiClient, times(1)).consultWithTools(eq(31L), eq("最近血压怎么样"), eq("{}"), anyList(), anyList());
    }

    @Test
    void enforcesConfiguredModelToolIterationLimit() {
        AiConsultationClient aiClient = mock(AiConsultationClient.class);
        when(aiClient.supportsToolCalling()).thenReturn(true);
        AgentProperties properties = new AgentProperties();
        properties.setModelToolCallingEnabled(true);
        properties.setMaxIterations(1);
        AgentToolExecutor toolExecutor = mock(AgentToolExecutor.class);
        when(toolExecutor.definitions()).thenReturn(List.of(new AiToolDefinition(
                "get_health_metrics", "读取健康指标", Map.of("type", "object"))));
        when(aiClient.consultWithTools(eq(31L), eq("最近血压怎么样"), eq("{}"), anyList(), anyList()))
                .thenReturn(new AiToolResponse("", List.of(new AiToolCall(
                        "call-1", "get_health_metrics", Map.of()))));
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                aiClient, properties, mock(AgentExecutionContextFactory.class), new RagProperties(),
                query -> RagSearchResult.empty(), (query, context) -> RagSearchResult.empty(), toolExecutor);

        assertThatThrownBy(() -> orchestrator.run(new AgentRequest(
                        context("run-model-tool-limit"), "最近血压怎么样", "{}")))
                .isInstanceOf(com.kangban.client.AiClientException.class)
                .hasMessageContaining("调用次数已达上限");
        verify(toolExecutor, never()).execute(any(), any());
    }

    private AgentExecutionContext context(String runId) {
        long now = System.currentTimeMillis() / 1000;
        return new AgentExecutionContext(9L, 15L, 2L, 31L,
                runId, "trace-" + runId, now - 1, now + 60);
    }
}
