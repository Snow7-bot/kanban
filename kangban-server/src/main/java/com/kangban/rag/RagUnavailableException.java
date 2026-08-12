package com.kangban.rag;

import com.kangban.client.AiClientException;

public class RagUnavailableException extends AiClientException {
    public RagUnavailableException(String userMessage) {
        super(userMessage);
    }
}
