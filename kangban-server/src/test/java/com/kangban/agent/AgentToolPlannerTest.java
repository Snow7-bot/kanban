package com.kangban.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolPlannerTest {

    private final AgentToolPlanner planner = new AgentToolPlanner();

    @Test
    void selectsRelevantToolsAndMetricFromChineseQuestion() {
        List<AgentToolCall> calls = planner.plan("最近血压和用药情况怎么样？", 5);

        assertThat(calls).extracting(AgentToolCall::name)
                .containsExactly("get_health_metrics", "get_active_medications");
        assertThat(calls.get(0).arguments()).containsEntry("metric", "blood_pressure");
    }

    @Test
    void fallsBackToPatientSnapshotForGeneralQuestion() {
        List<AgentToolCall> calls = planner.plan("我最近总觉得不舒服", 5);

        assertThat(calls).extracting(AgentToolCall::name)
                .containsExactly("get_patient_health_snapshot");
    }

    @Test
    void capsPlannedCallsByConfiguredIterations() {
        List<AgentToolCall> calls = planner.plan("血压、用药、病历和检查报告", 2);

        assertThat(calls).hasSize(2);
        assertThat(calls).extracting(AgentToolCall::name)
                .containsExactly("get_health_metrics", "get_active_medications");
    }
}
