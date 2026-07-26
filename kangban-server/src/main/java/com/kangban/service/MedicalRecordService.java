package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangban.common.BusinessException;
import com.kangban.common.PageResult;
import com.kangban.common.Result;
import com.kangban.entity.FamilyMember;
import com.kangban.entity.MedicalRecord;
import com.kangban.entity.OcrAnalysisTask;
import com.kangban.mapper.MedicalRecordMapper;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.OcrAnalysisTaskMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png");

    private final MedicalRecordMapper medicalRecordMapper;
    private final OcrAnalysisTaskMapper ocrAnalysisTaskMapper;
    private final MinioService minioService;
    private final OcrTaskRunner ocrTaskRunner;
    private final FamilyMemberMapper familyMemberMapper;

    public PageResult<Map<String, Object>> list(Long userId, Integer page, Integer pageSize, Long memberId) {
        validateMemberAccess(userId, memberId);
        Page<MedicalRecord> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<MedicalRecord> wrapper = new LambdaQueryWrapper<MedicalRecord>()
                .eq(MedicalRecord::getUserId, userId)
                .isNull(MedicalRecord::getDeletedAt)
                .orderByDesc(MedicalRecord::getCreatedAt);
        if (memberId == null) {
            wrapper.isNull(MedicalRecord::getMemberId);
        } else {
            wrapper.eq(MedicalRecord::getMemberId, memberId);
        }

        Page<MedicalRecord> result = medicalRecordMapper.selectPage(pageParam, wrapper);

        List<Map<String, Object>> list = result.getRecords().stream().map(this::toMap).toList();
        return PageResult.of(list, result.getTotal(), page, pageSize);
    }

    public Result<Map<String, Object>> getById(Long userId, Long id) {
        MedicalRecord record = medicalRecordMapper.selectOne(
                new LambdaQueryWrapper<MedicalRecord>()
                        .eq(MedicalRecord::getId, id)
                        .eq(MedicalRecord::getUserId, userId)
                        .isNull(MedicalRecord::getDeletedAt));
        if (record == null) {
            throw BusinessException.notFound("病历记录不存在");
        }
        return Result.success(toMap(record));
    }

    public Map<String, Object> upload(Long userId, Long memberId, MultipartFile file) {
        validateMemberAccess(userId, memberId);
        // Validate file
        if (file.isEmpty()) {
            throw BusinessException.paramsError("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.paramsError("文件大小不能超过10MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw BusinessException.paramsError("仅支持PDF、JPG、PNG格式文件");
        }

        // Upload to MinIO
        String fileUrl = minioService.uploadFile(file, userId);

        // Create medical record
        MedicalRecord record = new MedicalRecord();
        record.setUserId(userId);
        record.setMemberId(memberId);
        record.setRecordName(originalName != null ? originalName : "未命名病历");
        record.setRecordType(ext.toUpperCase());
        record.setFileUrl(fileUrl);
        record.setFileSize(file.getSize());
        record.setFileType(file.getContentType());
        record.setStatus("pending");
        record.setCreatedAt(LocalDateTime.now());
        medicalRecordMapper.insert(record);

        // Create OCR analysis task
        OcrAnalysisTask task = new OcrAnalysisTask();
        task.setRecordId(record.getId());
        task.setUserId(userId);
        task.setStatus("pending");
        task.setProgress(0);
        task.setCreatedAt(LocalDateTime.now());
        ocrAnalysisTaskMapper.insert(task);

        // Run OCR analysis asynchronously through a separate Spring bean.
        ocrTaskRunner.processOcr(task.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", record.getId());
        result.put("taskId", task.getId());
        return result;
    }

    public void delete(Long userId, Long id) {
        MedicalRecord record = medicalRecordMapper.selectOne(
                new LambdaQueryWrapper<MedicalRecord>()
                        .eq(MedicalRecord::getId, id)
                        .eq(MedicalRecord::getUserId, userId)
                        .isNull(MedicalRecord::getDeletedAt));
        if (record == null) {
            throw BusinessException.notFound("病历记录不存在");
        }
        record.setDeletedAt(LocalDateTime.now());
        medicalRecordMapper.updateById(record);
    }

    public Map<String, Object> getAnalysisStatus(Long userId, Long id) {
        MedicalRecord record = medicalRecordMapper.selectOne(
                new LambdaQueryWrapper<MedicalRecord>()
                        .eq(MedicalRecord::getId, id)
                        .eq(MedicalRecord::getUserId, userId)
                        .isNull(MedicalRecord::getDeletedAt));
        if (record == null) {
            throw BusinessException.notFound("病历记录不存在");
        }

        OcrAnalysisTask task = ocrAnalysisTaskMapper.selectOne(
                new LambdaQueryWrapper<OcrAnalysisTask>()
                        .eq(OcrAnalysisTask::getRecordId, id)
                        .orderByDesc(OcrAnalysisTask::getCreatedAt)
                        .last("LIMIT 1"));

        Map<String, Object> result = new HashMap<>();
        if (task != null) {
            result.put("status", task.getStatus());
            result.put("progress", task.getProgress());
            result.put("confidence", record.getConfidence());
            result.put("taskId", task.getId());
        } else {
            result.put("status", record.getStatus());
            result.put("progress", record.getStatus().equals("completed") ? 100 : 0);
            result.put("confidence", record.getConfidence());
        }
        return result;
    }

    /**
     * 导出病历 PDF — 返回真实 PDF 文件流
     */
    public void printPdf(Long userId, Long id, boolean includeAnalysis, HttpServletResponse response) {
        MedicalRecord record = medicalRecordMapper.selectOne(
                new LambdaQueryWrapper<MedicalRecord>()
                        .eq(MedicalRecord::getId, id)
                        .eq(MedicalRecord::getUserId, userId)
                        .isNull(MedicalRecord::getDeletedAt));
        if (record == null) {
            throw BusinessException.notFound("病历记录不存在");
        }
        log.info("User {} requested PDF print for medical record {}", userId, id);

        try {
            byte[] fileBytes = minioService.downloadByUrl(record.getFileUrl());
            boolean isPdf = "application/pdf".equalsIgnoreCase(record.getFileType()) ||
                    (record.getRecordType() != null && record.getRecordType().equalsIgnoreCase("PDF"));

            if (isPdf) {
                if (includeAnalysis && hasAnalysisData(record)) {
                    byte[] merged = mergePdfWithAnalysis(fileBytes, record);
                    writePdfResponse(response, merged, record.getRecordName());
                } else {
                    writePdfResponse(response, fileBytes, record.getRecordName());
                }
            } else {
                // Image → wrap in PDF
                byte[] pdfBytes = imageToPdf(fileBytes, record, includeAnalysis);
                writePdfResponse(response, pdfBytes, record.getRecordName());
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PDF generation failed for record {}", id, e);
            throw new RuntimeException("PDF生成失败: " + e.getMessage());
        }
    }

    private boolean hasAnalysisData(MedicalRecord record) {
        return (record.getDiagnosisData() != null && !record.getDiagnosisData().isEmpty()) ||
               (record.getOcrText() != null && !record.getOcrText().isEmpty());
    }

    private byte[] mergePdfWithAnalysis(byte[] originalPdfBytes, MedicalRecord record) throws Exception {
        try (PDDocument doc = Loader.loadPDF(originalPdfBytes)) {
            addAnalysisPage(doc, record);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] imageToPdf(byte[] imageBytes, MedicalRecord record, boolean includeAnalysis) throws Exception {
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (bufferedImage == null) {
            throw BusinessException.paramsError("无法解析图片文件");
        }

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float pageWidth = PDRectangle.A4.getWidth();
            float pageHeight = PDRectangle.A4.getHeight();
            float imgWidth = bufferedImage.getWidth();
            float imgHeight = bufferedImage.getHeight();
            float scale = Math.min(pageWidth / imgWidth, pageHeight / imgHeight) * 0.9f;
            float x = (pageWidth - imgWidth * scale) / 2;
            float y = (pageHeight - imgHeight * scale) / 2;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bufferedImage);
                cs.drawImage(pdImage, x, y, imgWidth * scale, imgHeight * scale);
            }

            if (includeAnalysis && hasAnalysisData(record)) {
                addAnalysisPage(doc, record);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private void addAnalysisPage(PDDocument doc, MedicalRecord record) throws Exception {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float margin = 50;
            float y = PDRectangle.A4.getHeight() - margin;
            float lineHeight = 18;

            // Title
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
            cs.newLineAtOffset(margin, y);
            cs.showText("AI \u5206\u6790\u62a5\u544a");
            cs.endText();
            y -= lineHeight * 2;

            // Metadata line
            StringBuilder meta = new StringBuilder();
            if (record.getHospital() != null) meta.append("\u533b\u9662: ").append(record.getHospital()).append("  ");
            if (record.getDepartment() != null) meta.append("\u79d1\u5ba4: ").append(record.getDepartment()).append("  ");
            if (record.getRecordDate() != null) meta.append("\u65e5\u671f: ").append(record.getRecordDate());
            if (meta.length() > 0) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.newLineAtOffset(margin, y);
                cs.showText(meta.toString().trim());
                cs.endText();
                y -= lineHeight * 2;
            }

            // OCR text section
            if (record.getOcrText() != null && !record.getOcrText().isEmpty()) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("OCR \u8bc6\u522b\u6587\u672c:");
                cs.endText();
                y -= lineHeight;

                String ocrText = record.getOcrText();
                int maxChars = 80;
                for (int i = 0; i < ocrText.length() && y > margin + 40; i += maxChars) {
                    int end = Math.min(i + maxChars, ocrText.length());
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(ocrText.substring(i, end));
                    cs.endText();
                    y -= lineHeight;
                }
                y -= lineHeight;
            }

            // Diagnosis data section
            if (record.getDiagnosisData() != null && !record.getDiagnosisData().isEmpty()) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("\u8bca\u65ad\u6570\u636e:");
                cs.endText();
                y -= lineHeight;

                String diag = record.getDiagnosisData();
                int maxChars = 80;
                for (int i = 0; i < diag.length() && y > margin + 40; i += maxChars) {
                    int end = Math.min(i + maxChars, diag.length());
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(diag.substring(i, end));
                    cs.endText();
                    y -= lineHeight;
                }
                y -= lineHeight;
            }

            // Footer
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE), 8);
            cs.newLineAtOffset(margin, margin);
            cs.showText("\u7531\u5eb7\u4f34\u667a\u80fd\u533b\u7597\u52a9\u624b\u751f\u6210 \u2014 \u4ec5\u4f9b\u53c2\u8003\uff0c\u4e0d\u6784\u6210\u533b\u7597\u5efa\u8bae");
            cs.endText();
        }
    }

    private void writePdfResponse(HttpServletResponse response, byte[] pdfBytes, String filename) throws Exception {
        response.setContentType("application/pdf");
        String safeName = (filename != null ? filename.replaceAll("\\.\\w+$", "") : "\u75c5\u5386");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + URLEncoder.encode(safeName + ".pdf", "UTF-8") + "\"");
        response.setContentLength(pdfBytes.length);
        response.getOutputStream().write(pdfBytes);
        response.getOutputStream().flush();
    }

    private Map<String, Object> toMap(MedicalRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("userId", record.getUserId());
        map.put("memberId", record.getMemberId());
        map.put("recordName", record.getRecordName());
        map.put("recordType", record.getRecordType());
        map.put("hospital", record.getHospital());
        map.put("department", record.getDepartment());
        map.put("doctor", record.getDoctor());
        map.put("recordDate", record.getRecordDate());
        map.put("fileUrl", record.getFileUrl());
        map.put("fileSize", record.getFileSize());
        map.put("fileType", record.getFileType());
        map.put("status", record.getStatus());
        map.put("confidence", record.getConfidence());
        map.put("ocrText", record.getOcrText());
        map.put("diagnosisData", record.getDiagnosisData());
        map.put("medicationsData", record.getMedicationsData());
        map.put("advicesData", record.getAdvicesData());
        map.put("createdAt", record.getCreatedAt());
        return map;
    }

    private void validateMemberAccess(Long userId, Long memberId) {
        if (memberId == null) return;
        Long count = familyMemberMapper.selectCount(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, memberId)
                        .eq(FamilyMember::getUserId, userId)
                        .isNull(FamilyMember::getDeletedAt)
        );
        if (count == 0) {
            throw BusinessException.notFound("家庭成员不存在或无权访问");
        }
    }

}
