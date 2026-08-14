package com.kangban.agent;

import com.kangban.common.BusinessException;
import com.kangban.service.FamilyAccessService;
import com.kangban.service.PatientHealthContextService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PatientHealthSnapshotToolTest {

    @Test
    void readsSnapshotFromAuthorizedServerContextAndIgnoresIdentityArguments() {
        PatientHealthContextService contextService = mock(PatientHealthContextService.class);
        FamilyAccessService accessService = mock(FamilyAccessService.class);
        PatientHealthSnapshotTool tool = new PatientHealthSnapshotTool(contextService, accessService);
        PatientHealthContextService.Snapshot snapshot = new PatientHealthContextService.Snapshot(
                2L, "王阿姨", Map.of("name", "王阿姨"),
                "{\"subject\":{\"name\":\"王阿姨\"},\"selectedMemberId\":2}",
                "已读取王阿姨的健康档案。");
        when(contextService.build(15L, 2L)).thenReturn(snapshot);
        AgentExecutionContext context = context(9L, 15L, 2L);

        AgentToolResult result = tool.execute(context,
                Map.of("subjectUserId", 999L, "memberId", 888L));

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.SUCCESS);
        assertThat(result.content()).contains("王阿姨").doesNotContain("999").doesNotContain("888");
        verify(accessService).require(9L, 15L, FamilyAccessService.Scope.USE_AI);
        verify(contextService).build(15L, 2L);
        verifyNoMoreInteractions(accessService, contextService);
    }

    @Test
    void blocksFamilyAccessBeforeReadingPatientData() {
        PatientHealthContextService contextService = mock(PatientHealthContextService.class);
        FamilyAccessService accessService = mock(FamilyAccessService.class);
        PatientHealthSnapshotTool tool = new PatientHealthSnapshotTool(contextService, accessService);
        doThrow(BusinessException.forbidden("未获得该家庭成员的数据访问权限"))
                .when(accessService).require(9L, 15L, FamilyAccessService.Scope.USE_AI);

        AgentToolResult result = tool.execute(context(9L, 15L, 2L), Map.of());

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.BLOCKED);
        assertThat(result.errorCode()).isEqualTo("ACCESS_DENIED");
        assertThat(result.content()).doesNotContain("未获得");
        verifyNoInteractions(contextService);
    }

    @Test
    void blocksInvalidPatientScopeWithoutLeakingBusinessDetails() {
        PatientHealthContextService contextService = mock(PatientHealthContextService.class);
        FamilyAccessService accessService = mock(FamilyAccessService.class);
        PatientHealthSnapshotTool tool = new PatientHealthSnapshotTool(contextService, accessService);
        when(contextService.build(15L, 404L))
                .thenThrow(BusinessException.notFound("家庭成员不存在或无权访问"));

        AgentToolResult result = tool.execute(context(9L, 15L, 404L), Map.of());

        assertThat(result.status()).isEqualTo(AgentToolResult.Status.BLOCKED);
        assertThat(result.errorCode()).isEqualTo("PATIENT_SCOPE_INVALID");
        assertThat(result.content()).isEqualTo("当前账号无权读取该患者健康数据。");
    }

    private AgentExecutionContext context(Long actor, Long subject, Long member) {
        long now = Instant.now().getEpochSecond();
        return new AgentExecutionContext(actor, subject, member, 31L,
                "run-snapshot", "trace-snapshot", now - 1, now + 60);
    }
}
