package com.kangban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.client.AiConsultationClient;
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
    private AiConsultationClient aiClient;
    private PatientHealthContextService patientHealthContextService;
    private ConsultationService service;

    @BeforeEach
    void setUp() {
        sessionMapper = mock(ChatSessionMapper.class);
        messageMapper = mock(ChatMessageMapper.class);
        aiClient = mock(AiConsultationClient.class);
        patientHealthContextService = mock(PatientHealthContextService.class);
        service = new ConsultationService(
                sessionMapper, messageMapper, new ObjectMapper(), aiClient, patientHealthContextService);
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
        verifyNoInteractions(aiClient);
        assertThat(sessionCaptor.getValue().getPatientData()).contains("family-agent-v2");
        assertThat(messageCaptor.getValue().getSessionId()).isEqualTo(31L);
        assertThat(messageCaptor.getValue().getRole()).isEqualTo("assistant");
        assertThat(messageCaptor.getValue().getContent()).isEqualTo("王阿姨最新健康概况");
        assertThat(result.getData()).isSameAs(messageCaptor.getValue());
    }

    @Test
    void streamPersistsOneReplyLinkedToTheExactUserMessage() {
        ChatSession session = session(3L, 9L);
        ChatMessage userMessage = userMessage(21L, 3L, 9L, "client-1");
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectOne(any())).thenReturn(userMessage, null, null);
        when(patientHealthContextService.build(9L, null)).thenReturn(snapshot());
        when(aiClient.consult(3L, "头痛两天", "{}")).thenReturn("请注意休息并观察症状。");

        service.streamAiResponse(9L, 3L, 21L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(aiClient, times(1)).consult(3L, "头痛两天", "{}");
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

        verifyNoInteractions(aiClient);
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
}
