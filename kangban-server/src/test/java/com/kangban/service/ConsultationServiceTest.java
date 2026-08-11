package com.kangban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.AgentExecutionContext;
import com.kangban.agent.AgentOrchestrator;
import com.kangban.agent.AgentResponse;
import com.kangban.dto.request.CreateSessionRequest;
import com.kangban.dto.request.SendMessageRequest;
import com.kangban.entity.ChatMessage;
import com.kangban.entity.ChatSession;
import com.kangban.mapper.ChatMessageMapper;
import com.kangban.mapper.ChatSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ConsultationServiceTest {

    private ChatSessionMapper sessionMapper;
    private ChatMessageMapper messageMapper;
    private AgentOrchestrator agentOrchestrator;
    private PatientHealthContextService patientHealthContextService;
    private FamilyAccessService familyAccessService;
    private AuditService auditService;
    private ConsultationService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(ChatSessionMapper.class);
        messageMapper = mock(ChatMessageMapper.class);
        agentOrchestrator = mock(AgentOrchestrator.class);
        patientHealthContextService = mock(PatientHealthContextService.class);
        familyAccessService = mock(FamilyAccessService.class);
        auditService = mock(AuditService.class);
        when(familyAccessService.require(anyLong(), any(), any())).thenAnswer(invocation -> {
            Long actor = invocation.getArgument(0);
            Long subject = invocation.getArgument(1);
            return subject == null ? actor : subject;
        });
        service = new ConsultationService(
                sessionMapper, messageMapper, new ObjectMapper(), agentOrchestrator, patientHealthContextService,
                familyAccessService, auditService);
        ReflectionTestUtils.setField(service, "taskExecutor", (Executor) Runnable::run);
    }

    @Test
    void repeatedClientMessageIdReturnsExistingMessageWithoutDuplicateInsert() {
        ChatSession session = session(3L, 9L);
        ChatMessage existing = userMessage(21L, 3L, 9L, "client-1");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(existing);

        SendMessageRequest request = new SendMessageRequest();
        request.setContent("头痛两天");
        request.setClientMessageId("client-1");

        var result = service.sendMessage(9L, 3L, request);

        assertThat(result.getData().get("userMessage")).isSameAs(existing);
        verify(messageMapper, never()).insert(any());
    }

    @Test
    void createSessionUsesAuthorizedDatabaseSnapshotAndPersonalizedFirstMessage() {
        CreateSessionRequest request = new CreateSessionRequest();
        request.setTitle("家庭问诊");
        request.setMemberId(15L);
        request.setPatientData("{\"name\":\"伪造患者\"}");
        PatientHealthContextService.Snapshot snapshot = new PatientHealthContextService.Snapshot(
                15L, "王阿姨", java.util.Map.of("name", "王阿姨"),
                "{\"subject\":{\"name\":\"王阿姨\"}}", "王阿姨的数据库个性化分析");
        when(patientHealthContextService.build(9L, 15L)).thenReturn(snapshot);
        doAnswer(invocation -> {
            ChatSession value = invocation.getArgument(0);
            value.setId(31L);
            return 1;
        }).when(sessionMapper).insert(any(ChatSession.class));

        service.createSession(9L, request);

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(sessionMapper).insert(sessionCaptor.capture());
        verify(messageMapper).insert(messageCaptor.capture());
        assertThat(sessionCaptor.getValue().getMemberId()).isEqualTo(15L);
        assertThat(sessionCaptor.getValue().getPatientData()).contains("王阿姨").doesNotContain("伪造患者");
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("王阿姨的数据库个性化分析");
    }

    @Test
    void switchingBackToExistingSessionAppendsFreshDatabaseSummary() {
        ChatSession session = session(31L, 9L);
        session.setMemberId(15L);
        PatientHealthContextService.Snapshot snapshot = new PatientHealthContextService.Snapshot(
                15L, "王阿姨", java.util.Map.of("name", "王阿姨"),
                "{\"contextVersion\":\"family-agent-v2\"}", "王阿姨最新健康概况");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(patientHealthContextService.build(9L, 15L)).thenReturn(snapshot);

        var result = service.appendPatientSummary(9L, 31L);

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(sessionMapper).updateById(sessionCaptor.capture());
        verify(messageMapper).insert(messageCaptor.capture());
        verifyNoInteractions(agentOrchestrator);
        assertThat(sessionCaptor.getValue().getPatientData()).contains("family-agent-v2");
        assertThat(messageCaptor.getValue().getSessionId()).isEqualTo(31L);
        assertThat(messageCaptor.getValue().getRole()).isEqualTo("assistant");
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("王阿姨最新健康概况");
        assertThat(result.getData()).isSameAs(messageCaptor.getValue());
    }

    @Test
    void repeatedSummaryRequestDoesNotInsertAnIdenticalConsecutiveMessage() {
        ChatSession session = session(31L, 9L);
        session.setMemberId(15L);
        PatientHealthContextService.Snapshot snapshot = new PatientHealthContextService.Snapshot(
                15L, "王阿姨", java.util.Map.of("name", "王阿姨"),
                "{\"contextVersion\":\"family-agent-v2\"}", "王阿姨最新健康概况");
        ChatMessage existing = new ChatMessage();
        existing.setId(88L);
        existing.setSessionId(31L);
        existing.setUserId(9L);
        existing.setRole("assistant");
        existing.setContent("王阿姨最新健康概况");
        existing.setCreatedAt(LocalDateTime.now());
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(patientHealthContextService.build(9L, 15L)).thenReturn(snapshot);
        when(messageMapper.selectOne(any())).thenReturn(existing);

        var result = service.appendPatientSummary(9L, 31L);

        verify(messageMapper, never()).insert(any());
        assertThat(result.getMessage()).isEqualTo("健康概况无变化");
        assertThat(result.getData()).isSameAs(existing);
    }

    @Test
    void streamPersistsOneReplyLinkedToTheExactUserMessage() {
        ChatSession session = session(3L, 9L);
        ChatMessage userMessage = userMessage(21L, 3L, 9L, "client-1");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(userMessage, null, null);
        when(patientHealthContextService.build(9L, null)).thenReturn(snapshot());
        AgentExecutionContext context = context();
        when(agentOrchestrator.createContext(9L, 9L, null, 3L)).thenReturn(context);
        when(agentOrchestrator.run(any())).thenReturn(
                new AgentResponse("请注意休息并观察症状。", context.runId(), null, null));

        service.streamAiResponse(9L, 3L, 21L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(agentOrchestrator, times(1)).run(any());
        verify(messageMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getReplyToMessageId()).isEqualTo(21L);
        assertThat(captor.getValue().getRole()).isEqualTo("assistant");
    }

    @Test
    void retryReplaysStoredReplyWithoutCallingProviderAgain() {
        ChatSession session = session(3L, 9L);
        ChatMessage userMessage = userMessage(21L, 3L, 9L, "client-1");
        ChatMessage reply = new ChatMessage();
        reply.setId(22L);
        reply.setRole("assistant");
        reply.setContent("已生成的回复");
        reply.setReplyToMessageId(21L);
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(userMessage, reply);

        service.streamAiResponse(9L, 3L, 21L);

        verifyNoInteractions(agentOrchestrator);
        verify(messageMapper, never()).insert(any());
    }

    private static ChatSession session(Long id, Long userId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setPatientData("{}");
        return session;
    }

    private static ChatMessage userMessage(Long id, Long sessionId, Long userId, String clientMessageId) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setUserId(userId);
        message.setRole("user");
        message.setContent("头痛两天");
        message.setClientMessageId(clientMessageId);
        message.setCreatedAt(LocalDateTime.now());
        return message;
    }

    private static PatientHealthContextService.Snapshot snapshot() {
        return new PatientHealthContextService.Snapshot(
                null, "本人", java.util.Map.of("name", "本人"), "{}", "个性化分析");
    }

    private static AgentExecutionContext context() {
        return new AgentExecutionContext(9L, 9L, null, 3L,
                "run-test", "trace-test", 1L, Long.MAX_VALUE);
    }
}
