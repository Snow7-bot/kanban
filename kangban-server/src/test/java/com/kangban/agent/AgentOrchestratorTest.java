package com.kangban.agent;

import com.kangban.client.AiConsultationClient;
import com.kangban.rag.RagSearchResult;
import org.junit.jupiter.api.Test;

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
                org.mockito.ArgumentMatchers.contains("公共知识库证据"),
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
}
