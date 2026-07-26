package com.kangban.client;

/**
 * OCR client interface — pluggable implementation.
 * Mock for dev, real Vision API for production.
 */
public interface OcrClient {

    /**
     * Analyze a medical document image/PDF.
     *
     * @param taskId    OCR task ID (for logging)
     * @param fileUrl   MinIO file URL to the document
     * @param fileType  MIME type (application/pdf, image/jpeg, etc.)
     * @return OCR result with structured fields
     */
    OcrResult analyze(Long taskId, String fileUrl, String fileType);

    /**
     * @return true if this is a mock/demo implementation
     */
    default boolean isMock() { return false; }

    /** Structured OCR result */
    record OcrResult(
            String ocrText,
            String diagnosisConclusion,
            String findings,
            String advice,
            double confidence
    ) {
        public static OcrResult empty() {
            return new OcrResult("", "", "", "", 0.0);
        }

        public boolean hasText() {
            return ocrText != null && !ocrText.isBlank();
        }
    }
}
