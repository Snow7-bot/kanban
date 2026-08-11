package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.common.BusinessException;
import com.kangban.common.Result;
import com.kangban.agent.AgentExecutionContext;
import com.kangban.agent.AgentOrchestrator;
import com.kangban.agent.AgentRequest;
import com.kangban.agent.AgentResponse;
import com.kangban.dto.request.CreateSessionRequest;
import com.kangban.dto.request.SendMessageRequest;
import com.kangban.dto.request.UpdatePatientRequest;
import com.kangban.client.AiClientException;
import com.kangban.entity.ChatMessage;
import com.kangban.entity.ChatSession;
import com.kangban.mapper.ChatMessageMapper;
import com.kangban.mapper.ChatSessionMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationService {

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;
    private final AgentOrchestrator agentOrchestrator;
    private final PatientHealthContextService patientHealthContextService;
    private final FamilyAccessService familyAccessService;
    private final AuditService auditService;

    @Resource(name = "agentTaskExecutor")
    private Executor taskExecutor;

    private final Set<Long> activeMessageIds = ConcurrentHashMap.newKeySet();

    /**
     * 获取会话列表
     */
    public Result<List<ChatSession>> getSessions(Long userId, Long requestedSubjectUserId, Long memberId) {
        Long subjectUserId = familyAccessService.require(
                userId, requestedSubjectUserId, FamilyAccessService.Scope.USE_AI);
        patientHealthContextService.build(subjectUserId, memberId);
        LambdaQueryWrapper<ChatSession> query = new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .isNull(ChatSession::getDeletedAt)
                .orderByDesc(ChatSession::getUpdatedAt);
        if (userId.equals(subjectUserId)) {
            query.isNull(ChatSession::getSubjectUserId);
        } else {
            query.eq(ChatSession::getSubjectUserId, subjectUserId);
        }
        if (memberId == null) {
            query.isNull(ChatSession::getMemberId);
        } else {
            query.eq(ChatSession::getMemberId, memberId);
        }
        List<ChatSession> sessions = chatSessionMapper.selectList(query);
        return Result.success(sessions);
    }

    /**
     * 创建新会话
     */
    @Transactional
    public Result<ChatSession> createSession(Long userId, CreateSessionRequest req) {
        Long subjectUserId = familyAccessService.require(
                userId, req.getSubjectUserId(), FamilyAccessService.Scope.USE_AI);
        PatientHealthContextService.Snapshot snapshot =
                patientHealthContextService.build(subjectUserId, req.getMemberId());

        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setSubjectUserId(userId.equals(subjectUserId) ? null : subjectUserId);
        session.setMemberId(req.getMemberId());
        session.setTitle(req.getTitle());
        // Never trust client-provided medical context. Build it from authorized database records.
        session.setPatientData(snapshot.contextJson());
        session.setStatus("active");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.insert(session);

        // The first agent message is a deterministic, database-backed personalized summary.
        ChatMessage welcomeMessage = new ChatMessage();
        welcomeMessage.setSessionId(session.getId());
        welcomeMessage.setUserId(userId);
        welcomeMessage.setRole("assistant");
        welcomeMessage.setContent(snapshot.initialMessage());
        welcomeMessage.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(welcomeMessage);
        if (!userId.equals(subjectUserId)) {
            auditService.record(userId, "SHARED_AI_SESSION_CREATE", "user",
                    subjectUserId, "为授权家庭账号创建AI问诊会话");
        }

        return Result.success("创建成功", session);
    }

    /**
     * 获取会话消息列表
     */
    public Result<List<ChatMessage>> getMessages(Long userId, Long sessionId) {
        // Verify session belongs to user
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .isNull(ChatSession::getDeletedAt)
        );
        if (session == null) {
            return Result.error("会话不存在");
        }
        requireSessionAccess(userId, session);

        List<ChatMessage> messages = chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreatedAt)
        );
        return Result.success(messages);
    }

    /**
     * Rebuild and append the selected patient's latest database-backed health summary.
     */
    @Transactional
    public Result<ChatMessage> appendPatientSummary(Long userId, Long sessionId) {
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .isNull(ChatSession::getDeletedAt)
        );
        if (session == null) {
            throw BusinessException.notFound("会话不存在");
        }
        Long subjectUserId = requireSessionAccess(userId, session);

        PatientHealthContextService.Snapshot snapshot =
                patientHealthContextService.build(subjectUserId, session.getMemberId());
        session.setPatientData(snapshot.contextJson());
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        ChatMessage latest = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getCreatedAt)
                        .orderByDesc(ChatMessage::getId)
                        .last("LIMIT 1")
        );
        if (latest != null
                && "assistant".equals(latest.getRole())
                && latest.getReplyToMessageId() == null
                && snapshot.initialMessage().equals(latest.getContent())) {
            return Result.success("健康概况无变化", latest);
        }

        ChatMessage summary = new ChatMessage();
        summary.setSessionId(sessionId);
        summary.setUserId(userId);
        summary.setRole("assistant");
        summary.setContent(snapshot.initialMessage());
        summary.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(summary);
        return Result.success("健康概况已更新", summary);
    }

    /**
     * 发送消息
     */
    @Transactional
    public Result<Map<String, Object>> sendMessage(Long userId, Long sessionId, SendMessageRequest req) {
        // Verify session belongs to user
        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .isNull(ChatSession::getDeletedAt)
        );
        if (session == null) {
            return Result.error("会话不存在");
        }
        requireSessionAccess(userId, session);

        if (req.getClientMessageId() != null && !req.getClientMessageId().isBlank()) {
            ChatMessage existingMessage = chatMessageMapper.selectOne(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getUserId, userId)
                            .eq(ChatMessage::getSessionId, sessionId)
                            .eq(ChatMessage::getClientMessageId, req.getClientMessageId())
                            .eq(ChatMessage::getRole, "user")
                            .last("LIMIT 1")
            );
            if (existingMessage != null) {
                Map<String, Object> existingResult = new LinkedHashMap<>();
                existingResult.put("userMessage", existingMessage);
                return Result.success(existingResult);
            }
        }

        // Insert user message
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(req.getContent());
        userMessage.setAttachmentUrl(req.getAttachmentUrl());
        userMessage.setClientMessageId(req.getClientMessageId());
        userMessage.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(userMessage);

        // Update session
        session.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(session);

        // The SSE endpoint performs the single AI call and persists its response.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userMessage", userMessage);

        return Result.success(result);
    }

    /**
     * 流式生成AI回复 — SSE 端点使用。
     */
    public SseEmitter streamAiResponse(Long userId, Long sessionId, Long messageId) {
        SseEmitter emitter = new SseEmitter(120_000L);

        ChatSession session = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, userId)
                        .isNull(ChatSession::getDeletedAt)
        );
        if (session == null) {
            failEmitter(emitter, "会话不存在或已失效。");
            return emitter;
        }
        Long subjectUserId;
        try {
            subjectUserId = requireSessionAccess(userId, session);
        } catch (BusinessException e) {
            failEmitter(emitter, e.getMessage());
            return emitter;
        }

        ChatMessage userMsg = chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getId, messageId)
                        .eq(ChatMessage::getSessionId, sessionId)
                        .eq(ChatMessage::getUserId, userId)
                        .eq(ChatMessage::getRole, "user")
                        .last("LIMIT 1")
        );
        if (userMsg == null) {
            failEmitter(emitter, "消息不存在或无权访问。");
            return emitter;
        }

        ChatMessage existingReply = findReply(messageId);
        if (existingReply != null) {
            replayCompletedResponse(emitter, existingReply.getContent());
            return emitter;
        }

        if (!activeMessageIds.add(messageId)) {
            failEmitter(emitter, "该消息正在生成回复，请稍后重试。");
            return emitter;
        }

        try {
            taskExecutor.execute(() -> {
                long start = System.currentTimeMillis();
                log.info("SSE streaming start: sessionId={}, messageId={}", sessionId, messageId);
                try {
                    emitter.send(SseEmitter.event().name("thinking").data("正在分析您的症状..."));
                    familyAccessService.require(userId, subjectUserId, FamilyAccessService.Scope.USE_AI);
                    PatientHealthContextService.Snapshot currentSnapshot =
                            patientHealthContextService.build(subjectUserId, session.getMemberId());
                    session.setPatientData(currentSnapshot.contextJson());
                    session.setUpdatedAt(LocalDateTime.now());
                    chatSessionMapper.updateById(session);
                    AgentExecutionContext context = agentOrchestrator.createContext(
                            userId, subjectUserId, session.getMemberId(), sessionId);
                    AgentResponse agentResponse = agentOrchestrator.run(new AgentRequest(
                            context, userMsg.getContent(), currentSnapshot.contextJson()));
                    String fullResponse = agentResponse.content();

                    // Save first: if the browser disconnects, retry can replay the same reply.
                    ChatMessage completedReply = findReply(messageId);
                    if (completedReply != null) {
                        fullResponse = completedReply.getContent();
                    } else {
                        ChatMessage aiMessage = new ChatMessage();
                        aiMessage.setSessionId(sessionId);
                        aiMessage.setUserId(userId);
                        aiMessage.setRole("assistant");
                        aiMessage.setContent(fullResponse);
                        aiMessage.setReplyToMessageId(messageId);
                        aiMessage.setCreatedAt(LocalDateTime.now());
                        chatMessageMapper.insert(aiMessage);
                    }

                    emitter.send(SseEmitter.event().name("thinking_done").data(""));
                    emitter.send(SseEmitter.event().name("token").data(fullResponse));
                    emitter.send(SseEmitter.event().name("done").data(fullResponse));
                    emitter.complete();

                    long elapsed = System.currentTimeMillis() - start;
                    log.info("SSE streaming done: sessionId={}, messageId={}, runId={}, elapsed={}ms",
                            sessionId, messageId, agentResponse.runId(), elapsed);
                } catch (AiClientException e) {
                    log.warn("SSE AI provider failure: sessionId={}, messageId={}", sessionId, messageId);
                    failEmitter(emitter, e.getUserMessage());
                } catch (IOException e) {
                    log.warn("SSE client disconnected: sessionId={}, messageId={}", sessionId, messageId);
                    emitter.complete();
                } catch (Exception e) {
                    log.error("SSE stream failed: sessionId={}, messageId={}, errorType={}",
                            sessionId, messageId, e.getClass().getSimpleName());
                    failEmitter(emitter, "AI 服务暂时不可用，请稍后重试。");
                } finally {
                    activeMessageIds.remove(messageId);
                }
            });
        } catch (RejectedExecutionException e) {
            activeMessageIds.remove(messageId);
            log.warn("SSE task rejected: sessionId={}, messageId={}", sessionId, messageId);
            failEmitter(emitter, "当前请求较多，请稍后重试。");
        }

        return emitter;
    }

    private ChatMessage findReply(Long messageId) {
        return chatMessageMapper.selectOne(
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getReplyToMessageId, messageId)
                        .eq(ChatMessage::getRole, "assistant")
                        .last("LIMIT 1")
        );
    }

    private void replayCompletedResponse(SseEmitter emitter, String response) {
        try {
            emitter.send(SseEmitter.event().name("thinking_done").data(""));
            emitter.send(SseEmitter.event().name("token").data(response));
            emitter.send(SseEmitter.event().name("done").data(response));
            emitter.complete();
        } catch (IOException e) {
            emitter.complete();
        }
    }

    private void failEmitter(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("ai_error").data(message));
            emitter.complete();
        } catch (IOException e) {
            emitter.complete();
        }
    }

    private Long requireSessionAccess(Long userId, ChatSession session) {
        Long subjectUserId = session.getSubjectUserId() == null
                ? userId : session.getSubjectUserId();
        return familyAccessService.require(userId, subjectUserId, FamilyAccessService.Scope.USE_AI);
    }

    /**
     * 获取历史会话（已完成的会话）
     */
    public Result<List<ChatSession>> getHistory(Long userId) {
        List<ChatSession> sessions = chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getStatus, "completed")
                        .isNull(ChatSession::getDeletedAt)
                        .orderByDesc(ChatSession::getUpdatedAt)
        );
        List<ChatSession> authorizedSessions = sessions.stream().filter(session -> {
            try {
                requireSessionAccess(userId, session);
                return true;
            } catch (BusinessException exception) {
                return false;
            }
        }).toList();
        return Result.success(authorizedSessions);
    }

    /**
     * 更新患者信息
     */
    @Transactional
    public void updatePatientProfile(Long userId, UpdatePatientRequest req) {
        // Update the active session's patient data
        ChatSession activeSession = chatSessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .eq(ChatSession::getStatus, "active")
                        .isNull(ChatSession::getSubjectUserId)
                        .isNull(ChatSession::getMemberId)
                        .isNull(ChatSession::getDeletedAt)
                        .last("LIMIT 1")
        );

        if (activeSession == null) {
            throw new BusinessException("没有活跃的会话");
        }

        Map<String, Object> patientData = readPatientData(activeSession.getPatientData());
        if (req.getName() != null) patientData.put("name", req.getName());
        if (req.getAge() != null) patientData.put("age", req.getAge());
        if (req.getGender() != null) patientData.put("gender", req.getGender());
        if (req.getChiefComplaint() != null) patientData.put("chiefComplaint", req.getChiefComplaint());
        if (req.getMedicalHistory() != null) patientData.put("medicalHistory", req.getMedicalHistory());
        if (req.getAllergyHistory() != null) patientData.put("allergyHistory", req.getAllergyHistory());

        try {
            activeSession.setPatientData(objectMapper.writeValueAsString(patientData));
        } catch (JsonProcessingException exception) {
            throw new BusinessException("患者资料保存失败");
        }
        activeSession.setUpdatedAt(LocalDateTime.now());
        chatSessionMapper.updateById(activeSession);
    }

    private Map<String, Object> readPatientData(String rawPatientData) {
        if (rawPatientData == null || rawPatientData.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(rawPatientData, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ignored) {
            return new LinkedHashMap<>();
        }
    }

}
