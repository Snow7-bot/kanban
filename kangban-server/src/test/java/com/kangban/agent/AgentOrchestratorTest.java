package com.kangban.agent;

import com.kangban.client.AiConsultationClient;
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
}
