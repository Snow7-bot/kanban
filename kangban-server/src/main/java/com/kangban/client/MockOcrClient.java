package com.kangban.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Mock OCR client — generates simulated analysis results for dev/testing.
 * Activated when app.ai.provider=mock (default).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockOcrClient implements OcrClient {

    @Override
    public OcrResult analyze(Long taskId, String fileUrl, String fileType) {
        log.info("Mock OCR analyze: taskId={}, fileType={}", taskId, fileType);
        try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        return new OcrResult(
                "【演示OCR识别结果】\n"
                        + "检查日期：" + LocalDateTime.now().toLocalDate() + "\n"
                        + "检查项目：示例病历文字\n"
                        + "内容仅用于开发演示，待人工确认。",
                "",
                "",
                "",
                0.0
        );
    }

    @Override
    public boolean isMock() { return true; }
}
