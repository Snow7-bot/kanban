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
import com.kangban.rag.PrivateKnowledgeIndexService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private final PrivateKnowledgeIndexService privateKnowledgeIndexService;

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
        validateFileSignature(file, ext);

        // Upload to MinIO
        String fileObject = minioService.uploadObject(file, userId);

        // Create medical record
        MedicalRecord record = new MedicalRecord();
        record.setUserId(userId);
        record.setMemberId(memberId);
        record.setRecordName(MinioService.sanitizeFilename(originalName));
        record.setRecordType(ext.toUpperCase());
        record.setFileUrl(fileObject);
        record.setFileSize(file.getSize());
        record.setFileType(canonicalContentType(ext));
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
        privateKnowledgeIndexService.revokeRecord(id, userId);
        minioService.deleteFile(record.getFileUrl());
    }

    /** 为历史 OCR 完成病历补建私有检索索引；不触发 OCR。 */
    public Map<String, Object> reindexPrivate(Long userId, Long id) {
        MedicalRecord record = medicalRecordMapper.selectOne(
                new LambdaQueryWrapper<MedicalRecord>()
                        .eq(MedicalRecord::getId, id)
                        .eq(MedicalRecord::getUserId, userId)
                        .isNull(MedicalRecord::getDeletedAt));
        if (record == null) {
            throw BusinessException.notFound("病历记录不存在");
        }
        privateKnowledgeIndexService.indexCompletedRecord(id);
        if (!isIndexable(record)) {
            return Map.of("recordId", id, "status", "skipped", "reason", "病历尚未完成 OCR 或没有可检索文本");
        }
        return Map.of("recordId", id, "status", "indexed");
    }

    /**
     * 显式补建当前账号的历史病历索引，不在应用启动时自动执行。
     */
    public Map<String, Object> reindexPrivateBatch(Long userId, Long memberId, Integer requestedLimit) {
        validateMemberAccess(userId, memberId);
        int limit = requestedLimit == null ? 100 : requestedLimit;
        if (limit < 1 || limit > 500) {
            throw BusinessException.paramsError("批量重建数量必须在1到500之间");
        }

        LambdaQueryWrapper<MedicalRecord> wrapper = new LambdaQueryWrapper<MedicalRecord>()
                .eq(MedicalRecord::getUserId, userId)
                .isNull(MedicalRecord::getDeletedAt)
                .orderByAsc(MedicalRecord::getId)
                .last("LIMIT " + limit);
        if (memberId == null) {
            wrapper.isNull(MedicalRecord::getMemberId);
        } else {
            wrapper.eq(MedicalRecord::getMemberId, memberId);
        }

        List<MedicalRecord> records = medicalRecordMapper.selectList(wrapper);
        int indexed = 0;
        int skipped = 0;
        int failed = 0;
        for (MedicalRecord record : records) {
            try {
                privateKnowledgeIndexService.indexCompletedRecord(record.getId());
                if (isIndexable(record)) {
                    indexed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
                log.warn("Private record reindex failed: recordId={}, errorType={}",
                        record.getId(), exception.getClass().getSimpleName());
            }
        }
        return Map.of(
                "scanned", records.size(),
                "indexed", indexed,
                "skipped", skipped,
                "failed", failed,
                "limit", limit,
                "memberId", memberId == null ? "self" : memberId
        );
    }

    private boolean isIndexable(MedicalRecord record) {
        return record != null
                && "completed".equalsIgnoreCase(record.getStatus())
                && String.join("\n\n",
                Optional.ofNullable(record.getOcrText()).orElse(""),
                Optional.ofNullable(record.getDiagnosisData()).orElse(""),
                Optional.ofNullable(record.getMedicationsData()).orElse(""),
                Optional.ofNullable(record.getAdvicesData()).orElse("")).trim().length() > 0;
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

    byte[] mergePdfWithAnalysis(byte[] originalPdfBytes, MedicalRecord record) throws Exception {
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

        int imageWidth = 1240;
        int imageHeight = 1754;
        int margin = 90;
        BufferedImage analysisImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = analysisImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, imageWidth, imageHeight);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(20, 27, 45));

            int y = margin;
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
            graphics.drawString("病历文本提取报告", margin, y);
            y += 58;

            StringBuilder meta = new StringBuilder();
            if (record.getHospital() != null) meta.append("医院：").append(record.getHospital()).append("  ");
            if (record.getDepartment() != null) meta.append("科室：").append(record.getDepartment()).append("  ");
            if (record.getRecordDate() != null) meta.append("日期：").append(record.getRecordDate());
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
            if (!meta.isEmpty()) {
                graphics.setColor(new Color(83, 91, 111));
                graphics.drawString(meta.toString().trim(), margin, y);
                y += 55;
            }

            graphics.setColor(new Color(20, 27, 45));
            if (record.getOcrText() != null && !record.getOcrText().isBlank()) {
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
                graphics.drawString("OCR 识别文本（需人工核对）", margin, y);
                y += 38;
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
                y = drawWrappedText(graphics, record.getOcrText(), margin, y,
                        imageWidth - margin * 2, 30, imageHeight - 260);
                y += 34;
            }

            if (record.getDiagnosisData() != null && !record.getDiagnosisData().isBlank()
                    && y < imageHeight - 320) {
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
                graphics.drawString("结构化提取数据", margin, y);
                y += 38;
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
                drawWrappedText(graphics, record.getDiagnosisData(), margin, y,
                        imageWidth - margin * 2, 30, imageHeight - 220);
            }

            graphics.setColor(new Color(104, 112, 132));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            graphics.drawString("由康伴生成，仅展示 OCR 提取内容；请核对原始病历，不构成医疗建议。",
                    margin, imageHeight - margin);
        } finally {
            graphics.dispose();
        }

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            PDImageXObject image = LosslessFactory.createFromImage(doc, analysisImage);
            cs.drawImage(image, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
        }
    }

    private int drawWrappedText(Graphics2D graphics, String text, int x, int startY,
                                int maxWidth, int lineHeight, int maxY) {
        FontMetrics metrics = graphics.getFontMetrics();
        int y = startY;
        for (String paragraph : text.replace("\r", "").split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (int index = 0; index < paragraph.length() && y <= maxY; index++) {
                char character = paragraph.charAt(index);
                if (!line.isEmpty() && metrics.stringWidth(line.toString() + character) > maxWidth) {
                    graphics.drawString(line.toString(), x, y);
                    y += lineHeight;
                    line.setLength(0);
                }
                line.append(character);
            }
            if (!line.isEmpty() && y <= maxY) {
                graphics.drawString(line.toString(), x, y);
                y += lineHeight;
            }
            y += lineHeight / 2;
        }
        return y;
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
        map.put("fileUrl", minioService.resolveFileUrl(record.getFileUrl()));
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

    private void validateFileSignature(MultipartFile file, String extension) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(8);
            boolean valid = switch (extension) {
                case "pdf" -> startsWith(header, new byte[]{'%', 'P', 'D', 'F', '-'});
                case "png" -> startsWith(header, new byte[]{
                        (byte) 0x89, 'P', 'N', 'G', '\r', '\n', (byte) 0x1A, '\n'});
                case "jpg", "jpeg" -> header.length >= 3
                        && header[0] == (byte) 0xFF
                        && header[1] == (byte) 0xD8
                        && header[2] == (byte) 0xFF;
                default -> false;
            };
            if (!valid) {
                throw BusinessException.paramsError("文件内容与扩展名不匹配");
            }
        } catch (IOException exception) {
            throw BusinessException.paramsError("无法读取上传文件");
        }
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private String canonicalContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
    }

}
