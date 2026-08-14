package com.kangban.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PatientHealthDataSectionToolsTest {

    private PatientHealthSnapshotTool snapshotTool;
    private PatientHealthDataSectionReader reader;
    private AgentExecutionContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = mock(PatientHealthSnapshotTool.class);
        reader = new PatientHealthDataSectionReader(
                snapshotTool, new ObjectMapper().findAndRegisterModules());
        long now = Instant.now().getEpochSecond();
        context = new AgentExecutionContext(9L, 15L, 2L, 31L,
                "run-sections", "trace-sections", now - 1, now + 60);
        when(snapshotTool.execute(any(), any())).thenReturn(AgentToolResult.success(
                "get_patient_health_snapshot", snapshot()));
    }

    @Test
    void healthMetricsReturnsOnlyRequestedMetricAndBoundedRecords() throws Exception {
        HealthMetricsTool tool = new HealthMetricsTool(reader);

        AgentToolResult result = tool.execute(context,
                Map.of("metric", "blood_pressure", "limit", 1, "subjectUserId", 999L));

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.SUCCESS);
        assertThat(result.toolName()).isEqualTo("get_health_metrics");
        assertThat(result.content()).contains("blood_pressure")
                .doesNotContain("heart_rate")
                .doesNotContain("阿司匹林")
                .doesNotContain("年度体检");
        assertThat(new ObjectMapper().readTree(result.content()).path("records")).hasSize(1);
    }

    @Test
    void medicationsAndRecordsExposeOnlyTheirOwnSections() throws Exception {
        ActiveMedicationsTool medications = new ActiveMedicationsTool(reader);
        RecentMedicalRecordsTool records = new RecentMedicalRecordsTool(reader);

        AgentToolResult medicationResult = medications.execute(context, Map.of("limit", 10));
        AgentToolResult recordResult = records.execute(context, Map.of("limit", 10));

        assertThat(medicationResult.content()).contains("阿司匹林")
                .doesNotContain("blood_pressure")
                .doesNotContain("年度体检");
        assertThat(recordResult.content()).contains("年度体检")
                .doesNotContain("blood_pressure")
                .doesNotContain("阿司匹林");
    }

    @Test
    void propagatesPermissionBlockWithoutReadingOrLeakingSnapshot() {
        when(snapshotTool.execute(any(), any())).thenReturn(
                AgentToolResult.blocked("get_patient_health_snapshot", "ACCESS_DENIED", "当前账号无权读取该患者健康数据。"));

        AgentToolResult result = new HealthMetricsTool(reader).execute(context, Map.of());

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.BLOCKED);
        assertThat(result.toolName()).isEqualTo("get_health_metrics");
        assertThat(result.errorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(result.content()).doesNotContain("王阿姨");
    }

    @Test
    void malformedSnapshotFailsSafely() {
        when(snapshotTool.execute(any(), any())).thenReturn(
                AgentToolResult.success("get_patient_health_snapshot", "not-json"));

        AgentToolResult result = new RecentMedicalRecordsTool(reader).execute(context, Map.of());

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.FAILED);
        assertThat(result.errorCode()).isEqualTo("SNAPSHOT_FORMAT_INVALID");
        assertThat(result.content()).isEqualTo("患者数据暂时无法读取。");
    }

    private String snapshot() {
        return """
                {
                  "contextVersion":"family-agent-v2",
                  "subject":{"name":"王阿姨","relation":"母亲"},
                  "selectedMemberId":2,
                  "dataWindow":{"healthMetrics":"最近30天","medicalRecords":"最近5份","medications":"当前有效用药"},
                  "healthMetrics":[
                    {"metric":"blood_pressure","value":"128/80","unit":"mmHg"},
                    {"metric":"heart_rate","value":"78","unit":"次/分"}
                  ],
                  "activeMedications":[{"name":"阿司匹林","dosage":"100","unit":"mg"}],
                  "recentMedicalRecords":[{"recordName":"年度体检报告","recordDate":"2026-08-12"}]
                }
                """;
    }
}
