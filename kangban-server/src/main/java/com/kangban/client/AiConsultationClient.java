package com.kangban.client;

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
     * @return true if this is a mock/demo implementation
     */
    default boolean isMock() { return false; }
}
