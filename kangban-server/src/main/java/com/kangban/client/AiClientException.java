package com.kangban.client;

/**
 * AI provider failure with a user-safe message.
 */
public class AiClientException extends RuntimeException {

    private final String userMessage;

    public AiClientException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
