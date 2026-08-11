package com.kangban.agent;

import com.kangban.client.AiClientException;
import com.kangban.client.AiConsultationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 康伴内置 Agent 的唯一编排入口。
 *
 * <p>第一阶段复用既有 AiConsultationClient，先统一身份上下文和调用边界；RAG 检索、工具循环
 * 和结构化引用在同一入口继续扩展，避免同步与 SSE 再各自复制一套模型调用逻辑。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private final AiConsultationClient aiClient;
    private final AgentProperties properties;
    private final AgentExecutionContextFactory contextFactory;

    public AgentExecutionContext createContext(Long actorUserId, Long subjectUserId,
                                               Long memberId, Long sessionId) {
        return contextFactory.create(actorUserId, subjectUserId, memberId, sessionId);
    }

    public AgentResponse run(AgentRequest request) {
        if (!properties.isEnabled()) {
            throw new AiClientException("AI 服务暂时不可用，请稍后重试。");
        }
        AgentExecutionContext context = request.context();
        if (context.expiredAt(Instant.now().getEpochSecond())) {
            throw new AiClientException("本次问诊上下文已失效，请重新发送。");
        }

        long startedAt = System.currentTimeMillis();
        log.info("Agent run start: runId={}, traceId={}, sessionId={}, actorUserId={}, subjectUserId={}, memberId={}",
                context.runId(), context.traceId(), context.sessionId(), context.actorUserId(),
                context.subjectUserId(), context.memberId());
        try {
            String content = aiClient.consult(
                    context.sessionId(), request.message(), request.patientContextJson());
            if (content == null || content.isBlank()) {
                throw new AiClientException("AI 服务暂未返回有效内容，请稍后重试。");
            }
            log.info("Agent run done: runId={}, elapsed={}ms, replyLength={}, citationCount=0, actionCount=0",
                    context.runId(), System.currentTimeMillis() - startedAt, content.length());
            return new AgentResponse(content, context.runId(), java.util.List.of(), java.util.List.of());
        } catch (AiClientException e) {
            log.warn("Agent provider failure: runId={}, elapsed={}ms", context.runId(),
                    System.currentTimeMillis() - startedAt);
            throw e;
        }
    }
}
