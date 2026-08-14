package com.kangban.client;

import com.kangban.agent.ConversationMessage;

import java.util.List;

/**
 * AI consultation client interface — pluggable implementation.
 * Mock for dev, real DeepSeek/OpenAI/etc for production.
 */
public interface AiConsultationClient {

    /**
     * Generate an AI response for a consultation message.
     *
     * @param sessionId   chat session ID (for logging only)
     * @param userContent user's message content
     * @param patientData optional patient context JSON
     * @return AI response text
     */
    String consult(Long sessionId, String userContent, String patientData);

    /**
     * 带受控历史窗口的对话调用。旧供应商默认退化为单轮调用，保证兼容性。
     */
    default String consult(Long sessionId,
                           String userContent,
                           String patientData,
                           List<ConversationMessage> conversationHistory) {
        return consult(sessionId, userContent, patientData);
    }

    /**
     * @return 当前供应商是否支持模型返回工具调用。
     * 默认关闭，保证旧供应商和 Mock 继续走兼容路径。
     */
    default boolean supportsToolCalling() { return false; }

    /**
     * 执行一次带工具定义的模型调用。供应商不支持时退化为一次普通文本调用。
     */
    default AiToolResponse consultWithTools(Long sessionId,
                                             String userContent,
                                             String patientData,
                                             List<AiToolDefinition> tools,
                                             List<AiToolMessage> history) {
        return new AiToolResponse(consult(sessionId, userContent, patientData), List.of());
    }

    /**
     * 带对话历史的工具调用。toolHistory 只表示当前轮工具循环，不能与持久化会话历史混用。
     */
    default AiToolResponse consultWithTools(Long sessionId,
                                             String userContent,
                                             String patientData,
                                             List<AiToolDefinition> tools,
                                             List<AiToolMessage> toolHistory,
                                             List<ConversationMessage> conversationHistory) {
        return consultWithTools(sessionId, userContent, patientData, tools, toolHistory);
    }

    /**
     * @return true if this is a mock/demo implementation
     */
    default boolean isMock() { return false; }
}
