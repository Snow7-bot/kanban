package com.kangban.service;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Qualifier("minioBucket")
    private final String minioBucket;

    /**
     * 上传文件到 MinIO
     *
     * @param file 上传的文件
     * @param userId 用户ID
     * @return 文件访问URL
     */
    public String uploadFile(MultipartFile file, Long userId) {
        return getFileUrl(uploadObject(file, userId));
    }

    /**
     * 上传文件并返回可长期保存的对象名称。
     */
    public String uploadObject(MultipartFile file, Long userId) {
        try {
            ensureBucket();
            String originalFilename = file.getOriginalFilename();
            String objectName = userId + "/" + UUID.randomUUID() + "-" + originalFilename;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            return objectName;
        } catch (Exception e) {
            log.error("MinIO上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(minioBucket)
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(minioBucket)
                    .build());
        }
    }

    /**
     * 从 MinIO 删除文件
     *
     * @param fileUrl 文件URL
     */
    public void deleteFile(String fileUrl) {
        try {
            // Extract object name from URL
            String objectName = extractObjectName(fileUrl);
            if (objectName != null) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectName)
                        .build());
            }
        } catch (Exception e) {
            log.error("MinIO删除失败", e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件预签名URL
     *
     * @param objectName 对象名称
     * @return 预签名URL
     */
    public String getFileUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(minioBucket)
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(24, TimeUnit.HOURS)
                    .build());
        } catch (Exception e) {
            log.error("获取MinIO文件URL失败", e);
            throw new RuntimeException("获取文件URL失败: " + e.getMessage());
        }
    }

    /**
     * 将数据库中的对象名称或历史预签名地址解析为新的访问地址。
     */
    public String resolveFileUrl(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }
        String objectName = storedValue.startsWith("http")
                ? extractObjectName(storedValue)
                : storedValue;
        if (objectName == null) {
            return storedValue;
        }
        try {
            return getFileUrl(objectName);
        } catch (RuntimeException ex) {
            log.warn("刷新文件访问地址失败，沿用原地址");
            return storedValue.startsWith("http") ? storedValue : null;
        }
    }

    /**
     * 获取文件字节流（用于PDF导出等场景）
     */
    public byte[] downloadFile(String objectName) {
        try {
            var response = minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                    .bucket(minioBucket)
                    .object(objectName)
                    .build());
            return response.readAllBytes();
        } catch (Exception e) {
            log.error("MinIO文件下载失败", e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 从URL获取文件字节
     */
    public byte[] downloadByUrl(String fileUrl) {
        String objectName = extractObjectName(fileUrl);
        if (objectName == null) {
            throw new RuntimeException("无法解析文件路径");
        }
        return downloadFile(objectName);
    }

    /**
     * 从URL中提取对象名称
     */
    private String extractObjectName(String fileUrl) {
        try {
            // URL format: http://endpoint/bucket/objectName?...
            String baseUrl = fileUrl.contains("?") ? fileUrl.substring(0, fileUrl.indexOf("?")) : fileUrl;
            String[] parts = baseUrl.split("/");
            // Find bucket position and take everything after it
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].equals(minioBucket) && i + 1 < parts.length) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = i + 1; j < parts.length; j++) {
                        if (sb.length() > 0) sb.append("/");
                        sb.append(parts[j]);
                    }
                    return sb.toString();
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("无法从URL提取对象名称: {}", fileUrl);
            return null;
        }
    }
}
