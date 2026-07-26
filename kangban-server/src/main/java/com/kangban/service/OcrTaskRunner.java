package com.kangban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.client.OcrClient;
import com.kangban.entity.MedicalRecord;
import com.kangban.entity.OcrAnalysisTask;
import com.kangban.mapper.MedicalRecordMapper;
import com.kangban.mapper.OcrAnalysisTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTaskRunner {

    private final OcrAnalysisTaskMapper ocrAnalysisTaskMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final OcrClient ocrClient;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    public void processOcr(Long taskId) {
        long start = System.currentTimeMillis();
        log.info("OCR task start: taskId={}", taskId);
        try {
            OcrAnalysisTask task = ocrAnalysisTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("OCR task not found: {}", taskId);
                return;
            }

            // Update to processing
            task.setStatus("processing");
            task.setProgress(20);
            task.setStartedAt(LocalDateTime.now());
            ocrAnalysisTaskMapper.updateById(task);

            // Get linked medical record for file info
            MedicalRecord record = medicalRecordMapper.selectById(task.getRecordId());
            if (record == null) {
                updateTaskError(taskId, "关联病历不存在");
                return;
            }

            task.setProgress(40);
            ocrAnalysisTaskMapper.updateById(task);

            // Delegate to OCR client
            OcrClient.OcrResult result = ocrClient.analyze(taskId, record.getFileUrl(), record.getFileType());
            if (result == null || !result.hasText()) {
                updateTaskError(taskId, "OCR 未返回可用文本");
                return;
            }

            task.setProgress(80);
            ocrAnalysisTaskMapper.updateById(task);

            // OCR only extracts source text. Any medical interpretation requires a separate consented flow.
            Map<String, Object> diagnosisData = new LinkedHashMap<>();
            diagnosisData.put("OCR说明", "文本由 OCR 提取，需人工确认");

            // Update task to completed
            task.setStatus("completed");
            task.setProgress(100);
            task.setResultData(toJson(diagnosisData));
            task.setCompletedAt(LocalDateTime.now());
            ocrAnalysisTaskMapper.updateById(task);

            // Update linked medical record
            record.setStatus("completed");
            record.setConfidence(result.confidence() > 0 ? (int) Math.round(result.confidence() * 100) : null);
            record.setOcrText(result.ocrText());
            record.setDiagnosisData(toJson(diagnosisData));
            record.setUpdatedAt(LocalDateTime.now());
            medicalRecordMapper.updateById(record);

            long elapsed = System.currentTimeMillis() - start;
            log.info("OCR task done: taskId={}, elapsed={}ms, mock={}", taskId, elapsed, ocrClient.isMock());
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("OCR task failed: taskId={}, elapsed={}ms, errorType={}",
                    taskId, elapsed, e.getClass().getSimpleName());
            updateTaskError(taskId, "OCR 服务处理失败，请稍后重试");
        }
    }

    private void updateTaskError(Long taskId, String errorMessage) {
        OcrAnalysisTask task = ocrAnalysisTaskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("failed");
            task.setErrorMessage(errorMessage);
            task.setProgress(0);
            ocrAnalysisTaskMapper.updateById(task);

            MedicalRecord record = medicalRecordMapper.selectById(task.getRecordId());
            if (record != null) {
                record.setStatus("failed");
                record.setUpdatedAt(LocalDateTime.now());
                medicalRecordMapper.updateById(record);
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
