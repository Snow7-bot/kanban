package com.kangban.client;

/**
 * Unified task status for AI and OCR operations.
 */
public enum TaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public String value() {
        return name().toLowerCase();
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
