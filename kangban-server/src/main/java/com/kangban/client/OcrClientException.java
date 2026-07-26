package com.kangban.client;

/** A provider failure that must leave the OCR task in the failed state. */
public class OcrClientException extends RuntimeException {
    public OcrClientException(String message) {
        super(message);
    }
}
