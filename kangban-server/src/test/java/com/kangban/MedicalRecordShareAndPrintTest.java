package com.kangban;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.common.Result;
import com.kangban.entity.MedicalRecord;
import com.kangban.entity.ShareRecord;
import com.kangban.entity.User;
import com.kangban.mapper.MedicalRecordMapper;
import com.kangban.mapper.ShareRecordMapper;
import com.kangban.mapper.UserMapper;
import com.kangban.service.MinioService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCaptchaConfig.class)
@DisplayName("P1-A: 病历分享与 PDF 导出集成测试")
class MedicalRecordShareAndPrintTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MedicalRecordMapper medicalRecordMapper;

    @Autowired
    private ShareRecordMapper shareRecordMapper;

    @MockitoBean
    private MinioService minioService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private User owner;
    private User otherUser;
    private MedicalRecord record;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        when(minioService.resolveFileUrl(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Clean up
        shareRecordMapper.delete(null);
        medicalRecordMapper.delete(null);
        userMapper.delete(null);

        // Create owner user
        owner = new User();
        owner.setUsername("13900000001");
        owner.setPhone("13900000001");
        owner.setPassword("$2a$10$dummyhashedpassword");
        owner.setName("测试用户A");
        owner.setStatus(1);
        owner.setCreatedAt(LocalDateTime.now());
        userMapper.insert(owner);

        // Create other user
        otherUser = new User();
        otherUser.setUsername("13900000002");
        otherUser.setPhone("13900000002");
        otherUser.setPassword("$2a$10$dummyhashedpassword2");
        otherUser.setName("测试用户B");
        otherUser.setStatus(1);
        otherUser.setCreatedAt(LocalDateTime.now());
        userMapper.insert(otherUser);

        // Create a medical record owned by owner
        record = new MedicalRecord();
        record.setUserId(owner.getId());
        record.setRecordName("血常规检验报告");
        record.setRecordType("PDF");
        record.setHospital("北京协和医院");
        record.setDepartment("检验科");
        record.setDoctor("张医生");
        record.setFileUrl("http://localhost:9000/test-bucket/1/test.pdf");
        record.setFileSize(1024L);
        record.setFileType("application/pdf");
        record.setStatus("completed");
        record.setConfidence(95);
        record.setOcrText("白细胞 7.5×10^9/L 红细胞 4.8×10^12/L 血小板 180×10^9/L");
        record.setDiagnosisData("{\"诊断结论\":\"血常规各项指标正常\"}");
        record.setCreatedAt(LocalDateTime.now());
        medicalRecordMapper.insert(record);

        // Generate JWT tokens
        ownerToken = generateToken(owner.getId().toString());
        otherToken = generateToken(otherUser.getId().toString());
    }

    private String generateToken(String userId) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }

    // ==================== 病历归属验证 ====================

    @Nested
    @DisplayName("病历归属验证")
    class OwnershipTests {

        @Test
        @DisplayName("所有者可以查看自己的病历")
        void ownerCanViewOwnRecord() throws Exception {
            mockMvc.perform(get("/medical-records/" + record.getId())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.recordName").value("血常规检验报告"));
        }

        @Test
        @DisplayName("其他用户无法查看不属于自己的病历")
        void otherUserCannotViewOthersRecord() throws Exception {
            mockMvc.perform(get("/medical-records/" + record.getId())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("其他用户无法删除不属于自己的病历")
        void otherUserCannotDeleteOthersRecord() throws Exception {
            mockMvc.perform(delete("/medical-records/" + record.getId())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== 分享功能 ====================

    @Nested
    @DisplayName("分享功能")
    class ShareTests {

        @Test
        @DisplayName("所有者可以生成分享链接")
        void ownerCanCreateShare() throws Exception {
            String response = mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.shareUrl").isNotEmpty())
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            // Verify share record exists in DB
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String token = (String) data.get("token");
            assertThat(token).isNotNull();

            ShareRecord share = shareRecordMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShareRecord>()
                            .eq(ShareRecord::getToken, token));
            assertThat(share).isNotNull();
            assertThat(share.getMedicalRecordId()).isEqualTo(record.getId());
            assertThat(share.getUserId()).isEqualTo(owner.getId());
            assertThat(share.getRevokedAt()).isNull();
        }

        @Test
        @DisplayName("其他用户无法为不属于自己的病历生成分享链接")
        void otherUserCannotCreateShare() throws Exception {
            mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("登录用户可以查看分享的病历")
        void authenticatedUserCanViewSharedRecord() throws Exception {
            // Create share first
            String shareResp = mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> shareResult = objectMapper.readValue(shareResp, Map.class);
            Map<String, Object> shareData = (Map<String, Object>) shareResult.get("data");
            String token = (String) shareData.get("token");

            // Other user views shared record
            mockMvc.perform(get("/share/" + token)
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.recordName").value("血常规检验报告"))
                    .andExpect(jsonPath("$.data.hospital").value("北京协和医院"));
        }

        @Test
        @DisplayName("未登录用户无法查看分享病历")
        void unauthenticatedUserCannotViewSharedRecord() throws Exception {
            // Create share
            String shareResp = mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> shareResult = objectMapper.readValue(shareResp, Map.class);
            Map<String, Object> shareData = (Map<String, Object>) shareResult.get("data");
            String token = (String) shareData.get("token");

            // No auth header
            mockMvc.perform(get("/share/" + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }

        @Test
        @DisplayName("查看已过期分享链接被拒绝")
        void expiredShareIsRejected() throws Exception {
            // Create an expired share
            ShareRecord expired = new ShareRecord();
            expired.setMedicalRecordId(record.getId());
            expired.setUserId(owner.getId());
            expired.setToken(UUID.randomUUID().toString().replace("-", ""));
            expired.setExpiresAt(LocalDateTime.now().minusHours(1));
            expired.setCreatedAt(LocalDateTime.now().minusDays(8));
            shareRecordMapper.insert(expired);

            mockMvc.perform(get("/share/" + expired.getToken())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(410))
                    .andExpect(jsonPath("$.message").value("分享已过期"));
        }

        @Test
        @DisplayName("查看已撤销分享链接被拒绝")
        void revokedShareIsRejected() throws Exception {
            // Create a revoked share
            ShareRecord revoked = new ShareRecord();
            revoked.setMedicalRecordId(record.getId());
            revoked.setUserId(owner.getId());
            revoked.setToken(UUID.randomUUID().toString().replace("-", ""));
            revoked.setExpiresAt(LocalDateTime.now().plusDays(7));
            revoked.setCreatedAt(LocalDateTime.now());
            revoked.setRevokedAt(LocalDateTime.now());
            shareRecordMapper.insert(revoked);

            mockMvc.perform(get("/share/" + revoked.getToken())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(410))
                    .andExpect(jsonPath("$.message").value("分享已被撤销"));
        }

        @Test
        @DisplayName("不存在或无效的分享令牌返回 404")
        void invalidTokenReturns404() throws Exception {
            mockMvc.perform(get("/share/nonexistent-token-12345")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("分享链接不存在"));
        }
    }

    // ==================== 撤销分享 ====================

    @Nested
    @DisplayName("撤销分享")
    class RevokeShareTests {

        @Test
        @DisplayName("所有者可以撤销分享")
        void ownerCanRevokeShare() throws Exception {
            // Create share
            mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk());

            // Revoke
            mockMvc.perform(delete("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // Verify revoked
            ShareRecord share = shareRecordMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShareRecord>()
                            .eq(ShareRecord::getMedicalRecordId, record.getId())
                            .eq(ShareRecord::getUserId, owner.getId())
                            .isNotNull(ShareRecord::getRevokedAt));
            assertThat(share).isNotNull();
        }

        @Test
        @DisplayName("撤销后分享链接不可访问")
        void revokedShareCannotBeAccessed() throws Exception {
            String shareResp = mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> shareResult = objectMapper.readValue(shareResp, Map.class);
            Map<String, Object> shareData = (Map<String, Object>) shareResult.get("data");
            String token = (String) shareData.get("token");

            // Revoke
            mockMvc.perform(delete("/medical-records/" + record.getId() + "/share")
                    .header("Authorization", "Bearer " + ownerToken));

            // Try to access
            mockMvc.perform(get("/share/" + token)
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(jsonPath("$.code").value(410))
                    .andExpect(jsonPath("$.message").value("分享已被撤销"));
        }

        @Test
        @DisplayName("其他用户不能撤销不属于自己的分享")
        void otherUserCannotRevokeShare() throws Exception {
            mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("重复撤销返回提示")
        void doubleRevokeReturnsMessage() throws Exception {
            mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/medical-records/" + record.getId() + "/share")
                    .header("Authorization", "Bearer " + ownerToken));

            mockMvc.perform(delete("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }

    // ==================== 分享状态查询 ====================

    @Nested
    @DisplayName("分享状态查询")
    class ShareStatusTests {

        @Test
        @DisplayName("未分享时返回 shared=false")
        void noShareReturnsFalse() throws Exception {
            mockMvc.perform(get("/medical-records/" + record.getId() + "/share-status")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.shared").value(false));
        }

        @Test
        @DisplayName("分享后返回 shared=true 含链接与有效期")
        void activeShareReturnsDetails() throws Exception {
            mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/medical-records/" + record.getId() + "/share-status")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.shared").value(true))
                    .andExpect(jsonPath("$.data.shareUrl").isNotEmpty())
                    .andExpect(jsonPath("$.data.expiresAt").isNotEmpty());
        }

        @Test
        @DisplayName("重复分享返回现有链接不创建新记录")
        void repeatShareReturnsExistingLink() throws Exception {
            String r1 = mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> m1 = objectMapper.readValue(r1, Map.class);
            String token1 = (String) ((Map<String, Object>) m1.get("data")).get("token");

            String r2 = mockMvc.perform(post("/medical-records/" + record.getId() + "/share")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> m2 = objectMapper.readValue(r2, Map.class);
            String token2 = (String) ((Map<String, Object>) m2.get("data")).get("token");

            assertThat(token1).isEqualTo(token2);
        }
    }

    // ==================== PDF 下载 ====================

    @Nested
    @DisplayName("PDF 下载")
    class PdfDownloadTests {

        @Test
        @DisplayName("所有者可以下载包含分析页的 PDF")
        void ownerCanDownloadPdfWithAnalysis() throws Exception {
            byte[] sourcePdf;
            try (PDDocument document = new PDDocument();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.addPage(new PDPage());
                document.save(output);
                sourcePdf = output.toByteArray();
            }
            when(minioService.downloadByUrl(record.getFileUrl())).thenReturn(sourcePdf);

            byte[] result = mockMvc.perform(get("/medical-records/" + record.getId() + "/print")
                            .param("includeAnalysis", "true")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/pdf"))
                    .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".pdf")))
                    .andReturn().getResponse().getContentAsByteArray();

            try (PDDocument downloaded = Loader.loadPDF(result)) {
                assertThat(downloaded.getNumberOfPages()).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("已认证用户无法下载不属于自己的病历 PDF")
        void otherUserCannotDownloadPdf() throws Exception {
            mockMvc.perform(get("/medical-records/" + record.getId() + "/print")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("未认证用户无法下载 PDF")
        void unauthenticatedCannotDownloadPdf() throws Exception {
            mockMvc.perform(get("/medical-records/" + record.getId() + "/print"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        }
    }
}
