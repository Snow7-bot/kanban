package com.kangban.agent;

import com.kangban.client.AiConsultationClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MedicalSafetyPolicyTest {

    @Test
    void routesPossibleEmergencyBeforeCallingTheModel() {
        AiConsultationClient client = mock(AiConsultationClient.class);
        AgentOrchestrator orchestrator = new AgentOrchestrator(
                client, new AgentProperties(), mock(AgentExecutionContextFactory.class));

        AgentResponse response = orchestrator.run(new AgentRequest(
                context(), "突然胸痛并且呼吸困难怎么办", "{}"));

        assertThat(response.content()).contains("120", "急诊", "不要等待 AI 回复");
        verifyNoInteractions(client);
    }

    @Test
    void replacesDefinitiveDiagnosisAndPrescriptionOutput() {
        assertThat(MedicalSafetyPolicy.guardResponse("您已经确诊为高血压，请服用 10mg 药物。"))
                .contains("不能根据在线对话为您确诊")
                .contains("开具处方")
                .doesNotContain("10mg");
    }

    @Test
    void promptRulesRequireEvidenceAndRejectMedicalOverreach() {
        assertThat(MedicalSafetyPolicy.promptRules())
                .contains("确诊结论")
                .contains("真实引用")
                .contains("未提供资料")
                .contains("线下急诊");
    }

    private AgentExecutionContext context() {
        long now = System.currentTimeMillis() / 1000;
        return new AgentExecutionContext(9L, 9L, null, 31L,
                "safety-run", "safety-trace", now - 1, now + 60);
    }
}
