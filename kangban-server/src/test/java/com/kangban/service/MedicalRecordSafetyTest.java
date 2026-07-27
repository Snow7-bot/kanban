package com.kangban.service;

import com.kangban.common.BusinessException;
import com.kangban.entity.MedicalRecord;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.MedicalRecordMapper;
import com.kangban.mapper.OcrAnalysisTaskMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MedicalRecordSafetyTest {

    @Test
    void rejectsAFileWhoseContentDoesNotMatchItsExtension() {
        MinioService minioService = mock(MinioService.class);
        MedicalRecordService service = service(minioService, mock(MedicalRecordMapper.class));
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThrows(BusinessException.class, () -> service.upload(9L, null, file));
        verifyNoInteractions(minioService);
    }

    @Test
    void storesAStableObjectNameInsteadOfAPresignedUrl() {
        MinioService minioService = mock(MinioService.class);
        MedicalRecordMapper medicalRecordMapper = mock(MedicalRecordMapper.class);
        MedicalRecordService service = service(minioService, medicalRecordMapper);
        MockMultipartFile file = new MockMultipartFile(
                "file", "../report.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8));
        when(minioService.uploadObject(file, 9L)).thenReturn("9/object-report.pdf");

        service.upload(9L, null, file);

        ArgumentCaptor<MedicalRecord> recordCaptor = ArgumentCaptor.forClass(MedicalRecord.class);
        verify(medicalRecordMapper).insert(recordCaptor.capture());
        assertEquals("9/object-report.pdf", recordCaptor.getValue().getFileUrl());
        assertEquals("report.pdf", recordCaptor.getValue().getRecordName());
        assertEquals("application/pdf", recordCaptor.getValue().getFileType());
    }

    @Test
    void rendersChineseAnalysisIntoAValidPdfPage() throws Exception {
        MedicalRecordService service = service(mock(MinioService.class), mock(MedicalRecordMapper.class));
        MedicalRecord record = new MedicalRecord();
        record.setHospital("测试医院");
        record.setDepartment("检验科");
        record.setOcrText("白细胞计数正常，原始病历内容需要人工核对。");
        record.setDiagnosisData("{\"OCR说明\":\"文本由 OCR 提取，需人工确认\"}");

        byte[] original;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            original = output.toByteArray();
        }

        byte[] result = service.mergePdfWithAnalysis(original, record);

        try (PDDocument document = Loader.loadPDF(result)) {
            assertEquals(2, document.getNumberOfPages());
            new PDFRenderer(document).renderImage(1);
        }
    }

    @Test
    void stripsPathsAndControlCharactersFromObjectFilenames() {
        assertEquals("report_.pdf", MinioService.sanitizeFilename("../folder/report\n.pdf"));
    }

    private MedicalRecordService service(MinioService minioService,
                                         MedicalRecordMapper medicalRecordMapper) {
        return new MedicalRecordService(
                medicalRecordMapper,
                mock(OcrAnalysisTaskMapper.class),
                minioService,
                mock(OcrTaskRunner.class),
                mock(FamilyMemberMapper.class)
        );
    }
}
