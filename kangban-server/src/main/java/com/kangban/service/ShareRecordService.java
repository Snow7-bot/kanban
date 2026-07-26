package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangban.common.BusinessException;
import com.kangban.common.Result;
import com.kangban.entity.MedicalRecord;
import com.kangban.entity.ShareRecord;
import com.kangban.mapper.MedicalRecordMapper;
import com.kangban.mapper.ShareRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareRecordService {

    private static final int TOKEN_BYTES = 32;
    private static final long DEFAULT_EXPIRY_HOURS = 168; // 7 days
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareRecordMapper shareRecordMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final MedicalRecordService medicalRecordService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    /**
     * 生成分享链接
     */
    @Transactional
    public Result<Map<String, Object>> createShare(Long userId, Long medicalRecordId) {
        // Verify record ownership
        medicalRecordService.getById(userId, medicalRecordId); // throws if not found

        // Check if already shared and not expired
        ShareRecord existing = shareRecordMapper.selectOne(
                new LambdaQueryWrapper<ShareRecord>()
                        .eq(ShareRecord::getMedicalRecordId, medicalRecordId)
                        .eq(ShareRecord::getUserId, userId)
                        .isNull(ShareRecord::getRevokedAt)
                        .ge(ShareRecord::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(ShareRecord::getCreatedAt)
                        .last("LIMIT 1"));

        if (existing != null) {
            // Return existing share
            return buildShareResponse(existing);
        }

        // Generate unique token
        String token;
        do {
            byte[] bytes = new byte[TOKEN_BYTES];
            RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (shareRecordMapper.selectCount(
                new LambdaQueryWrapper<ShareRecord>().eq(ShareRecord::getToken, token)) > 0);

        // Create share record
        ShareRecord record = new ShareRecord();
        record.setMedicalRecordId(medicalRecordId);
        record.setUserId(userId);
        record.setToken(token);
        record.setExpiresAt(LocalDateTime.now().plusHours(DEFAULT_EXPIRY_HOURS));
        record.setCreatedAt(LocalDateTime.now());
        shareRecordMapper.insert(record);

        log.info("Created share for medical record {} by user {}", medicalRecordId, userId);
        return buildShareResponse(record);
    }

    /**
     * 获取分享内容（需要登录验证）
     */
    public Result<Map<String, Object>> getSharedRecord(Long currentUserId, String token) {
        ShareRecord share = shareRecordMapper.selectOne(
                new LambdaQueryWrapper<ShareRecord>()
                        .eq(ShareRecord::getToken, token));

        if (share == null) {
            return Result.error(404, "分享链接不存在");
        }
        if (share.getRevokedAt() != null) {
            return Result.error(410, "分享已被撤销");
        }
        if (share.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Result.error(410, "分享已过期");
        }

        MedicalRecord record = medicalRecordMapper.selectById(share.getMedicalRecordId());
        if (record == null || record.getDeletedAt() != null) {
            return Result.error(404, "病历记录不存在");
        }

        // Build response - mask sensitive fields
        Map<String, Object> result = new HashMap<>();
        result.put("id", record.getId());
        result.put("recordName", record.getRecordName());
        result.put("recordType", record.getRecordType());
        result.put("hospital", record.getHospital());
        result.put("department", record.getDepartment());
        result.put("doctor", record.getDoctor());
        result.put("recordDate", record.getRecordDate());
        result.put("fileUrl", record.getFileUrl());
        result.put("fileSize", record.getFileSize());
        result.put("fileType", record.getFileType());
        result.put("status", record.getStatus());
        result.put("confidence", record.getConfidence());
        result.put("ocrText", record.getOcrText());
        result.put("diagnosisData", record.getDiagnosisData());
        result.put("medicationsData", record.getMedicationsData());
        result.put("advicesData", record.getAdvicesData());
        result.put("createdAt", record.getCreatedAt());

        return Result.success(result);
    }

    /**
     * 撤销分享
     */
    @Transactional
    public Result<Void> revokeShare(Long userId, Long medicalRecordId) {
        ShareRecord share = shareRecordMapper.selectOne(
                new LambdaQueryWrapper<ShareRecord>()
                        .eq(ShareRecord::getMedicalRecordId, medicalRecordId)
                        .eq(ShareRecord::getUserId, userId)
                        .isNull(ShareRecord::getRevokedAt));

        if (share == null) {
            return Result.error(404, "暂无活跃分享");
        }

        share.setRevokedAt(LocalDateTime.now());
        shareRecordMapper.updateById(share);

        log.info("Revoked share for medical record {} by user {}", medicalRecordId, userId);
        return Result.success("分享已撤销", null);
    }

    /**
     * 获取分享状态
     */
    public Result<Map<String, Object>> getShareStatus(Long userId, Long medicalRecordId) {
        // Verify ownership
        medicalRecordService.getById(userId, medicalRecordId);

        ShareRecord share = shareRecordMapper.selectOne(
                new LambdaQueryWrapper<ShareRecord>()
                        .eq(ShareRecord::getMedicalRecordId, medicalRecordId)
                        .eq(ShareRecord::getUserId, userId)
                        .isNull(ShareRecord::getRevokedAt)
                        .orderByDesc(ShareRecord::getCreatedAt)
                        .last("LIMIT 1"));

        Map<String, Object> result = new HashMap<>();
        if (share == null) {
            result.put("shared", false);
        } else if (share.getExpiresAt().isBefore(LocalDateTime.now())) {
            result.put("shared", false);
            result.put("expired", true);
        } else {
            result.put("shared", true);
            result.put("token", share.getToken());
            result.put("expiresAt", share.getExpiresAt().toString());
            result.put("shareUrl", baseUrl + "/#/shared-record/" + share.getToken());
        }
        return Result.success(result);
    }

    private Result<Map<String, Object>> buildShareResponse(ShareRecord share) {
        Map<String, Object> data = new HashMap<>();
        data.put("token", share.getToken());
        data.put("expiresAt", share.getExpiresAt().toString());
        data.put("shareUrl", baseUrl + "/#/shared-record/" + share.getToken());
        return Result.success("分享成功", data);
    }
}
