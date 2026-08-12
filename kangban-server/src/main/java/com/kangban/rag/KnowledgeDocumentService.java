package com.kangban.rag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.RagProperties;
import com.kangban.common.BusinessException;
import com.kangban.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class KnowledgeDocumentService {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentParser parser;
    private final TextChunker chunker;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;
    private final AuditService auditService;
    private final KnowledgeIngestionRunner ingestionRunner;

    public KnowledgeDocumentService(JdbcTemplate jdbcTemplate,
                                    DocumentParser parser,
                                    TextChunker chunker,
                                    EmbeddingClient embeddingClient,
                                    ObjectMapper objectMapper,
                                    RagProperties properties,
                                    AuditService auditService,
                                    KnowledgeIngestionRunner ingestionRunner) {
        this.jdbcTemplate = jdbcTemplate;
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.auditService = auditService;
        this.ingestionRunner = ingestionRunner;
    }

    @Transactional
    public Map<String, Object> upload(Long actorUserId, String title, String source,
                                      String sourceUrl, MultipartFile file) {
        byte[] bytes = readAndValidate(file);
        String fileName = safeFileName(file.getOriginalFilename());
        String sha256 = sha256(bytes);
        Map<String, Object> existing = findBySha256(sha256);
        if (existing != null) {
            existing.put("deduplicated", true);
            existing.put("jobId", null);
            return existing;
        }

        String finalTitle = title == null || title.isBlank() ? fileName : title.trim();
        String finalSource = source == null || source.isBlank() ? "待审核资料" : source.trim();
        KeyHolder documentKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO knowledge_documents "
                            + "(title, source, source_url, file_name, media_type, file_sha256, file_size, "
                            + "version, status, raw_content, created_by, updated_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'DRAFT', ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, finalTitle);
            statement.setString(2, finalSource);
            statement.setString(3, blankToNull(sourceUrl));
            statement.setString(4, fileName);
            statement.setString(5, file.getContentType());
            statement.setString(6, sha256);
            statement.setLong(7, bytes.length);
            statement.setBytes(8, bytes);
            statement.setLong(9, actorUserId);
            statement.setLong(10, actorUserId);
            return statement;
        }, documentKey);

        Long documentId = generatedId(documentKey);
        KeyHolder jobKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ingestion_jobs (document_id, job_type, status) VALUES (?, 'INGEST', 'PENDING')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, documentId);
            return statement;
        }, jobKey);
        Long jobId = generatedId(jobKey);
        auditService.record(actorUserId, "KNOWLEDGE_UPLOAD", "knowledge_document", documentId,
                "上传公共知识文档，等待解析和审核");
        scheduleIngestion(jobId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", documentId);
        result.put("jobId", jobId);
        result.put("status", KnowledgeDocumentStatus.DRAFT.name());
        result.put("deduplicated", false);
        return result;
    }

    @Transactional
    public void processJobNow(Long jobId) {
        Map<String, Object> job = findJob(jobId);
        if (job == null) {
            return;
        }
        Long documentId = number(job.get("document_id"));
        jdbcTemplate.update("UPDATE ingestion_jobs SET status='PROCESSING', updated_at=? WHERE id=?",
                LocalDateTime.now(), jobId);
        try {
            Map<String, Object> document = findDocumentWithRawContent(documentId);
            if (document == null) {
                throw BusinessException.notFound("知识文档不存在");
            }
            byte[] bytes = (byte[]) document.get("raw_content");
            ParsedDocument parsed = parser.parse(
                    String.valueOf(document.get("file_name")),
                    (String) document.get("media_type"), bytes);
            List<KnowledgeChunkDraft> chunks = chunker.chunk(parsed);
            if (chunks.isEmpty()) {
                throw BusinessException.paramsError("文档未生成有效知识片段");
            }
            jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE document_id=?", documentId);
            for (KnowledgeChunkDraft chunk : chunks) {
                jdbcTemplate.update(
                        "INSERT INTO knowledge_chunks "
                                + "(document_id, chunk_index, page_number, section, content, token_count, "
                                + "embedding_json, embedding_model) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        documentId, chunk.chunkIndex(), chunk.pageNumber(), blankToNull(chunk.section()),
                        chunk.content(), chunk.tokenCount(), serializeEmbedding(embeddingClient.embed(chunk.content())),
                        properties.getEmbeddingModel());
            }
            jdbcTemplate.update("UPDATE ingestion_jobs SET status='SUCCEEDED', total_chunks=?, "
                            + "processed_chunks=?, error_message=NULL, completed_at=?, updated_at=? WHERE id=?",
                    chunks.size(), chunks.size(), LocalDateTime.now(), LocalDateTime.now(), jobId);
            log.info("Knowledge ingestion done: jobId={}, documentId={}, chunks={}",
                    jobId, documentId, chunks.size());
        } catch (Exception e) {
            String message = safeError(e);
            jdbcTemplate.update("UPDATE ingestion_jobs SET status='FAILED', error_message=?, updated_at=? WHERE id=?",
                    message, LocalDateTime.now(), jobId);
            log.warn("Knowledge ingestion failed: jobId={}, documentId={}, errorType={}",
                    jobId, documentId, e.getClass().getSimpleName());
        }
    }

    public List<Map<String, Object>> list(String status) {
        String sql = "SELECT id, title, source, source_url, file_name, media_type, file_sha256, file_size, "
                + "version, status, review_note, created_by, updated_by, published_at, revoked_at, "
                + "created_at, updated_at, deleted_at FROM knowledge_documents WHERE deleted_at IS NULL ";
        if (status == null || status.isBlank()) {
            sql += "ORDER BY updated_at DESC";
            return jdbcTemplate.queryForList(sql);
        }
        sql += "AND status=? ORDER BY updated_at DESC";
        return jdbcTemplate.queryForList(sql, status.trim().toUpperCase(Locale.ROOT));
    }

    public Map<String, Object> getJob(Long jobId) {
        Map<String, Object> job = findJob(jobId);
        if (job == null) {
            throw BusinessException.notFound("入库任务不存在");
        }
        return job;
    }

    public List<Map<String, Object>> chunks(Long documentId) {
        if (findDocument(documentId) == null) {
            throw BusinessException.notFound("知识文档不存在");
        }
        return jdbcTemplate.queryForList("SELECT id, document_id, chunk_index, page_number, section, content, "
                + "token_count, embedding_model, created_at FROM knowledge_chunks "
                + "WHERE document_id=? ORDER BY chunk_index", documentId);
    }

    @Transactional
    public void submitReview(Long actorUserId, Long documentId) {
        transition(actorUserId, documentId, KnowledgeDocumentStatus.DRAFT,
                KnowledgeDocumentStatus.PENDING_REVIEW, "KNOWLEDGE_SUBMIT_REVIEW");
    }

    @Transactional
    public void publish(Long actorUserId, Long documentId, String reviewNote) {
        Map<String, Object> document = findDocument(documentId);
        if (document == null) {
            throw BusinessException.notFound("知识文档不存在");
        }
        if (!KnowledgeDocumentStatus.PENDING_REVIEW.name().equals(document.get("status"))) {
            throw BusinessException.conflict("只有待审核文档可以发布");
        }
        Integer chunks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_chunks WHERE document_id=?", Integer.class, documentId);
        if (chunks == null || chunks == 0) {
            throw BusinessException.conflict("文档尚未完成解析，不能发布");
        }
        jdbcTemplate.update("UPDATE knowledge_documents SET status='PUBLISHED', review_note=?, "
                        + "published_at=?, revoked_at=NULL, updated_by=?, updated_at=? WHERE id=? AND status='PENDING_REVIEW'",
                blankToNull(reviewNote), LocalDateTime.now(), actorUserId, LocalDateTime.now(), documentId);
        auditService.record(actorUserId, "KNOWLEDGE_PUBLISH", "knowledge_document", documentId,
                "审核通过并发布公共知识文档");
    }

    @Transactional
    public void revoke(Long actorUserId, Long documentId, String reason) {
        Map<String, Object> document = findDocument(documentId);
        if (document == null) {
            throw BusinessException.notFound("知识文档不存在");
        }
        if (KnowledgeDocumentStatus.REVOKED.name().equals(document.get("status"))) {
            return;
        }
        jdbcTemplate.update("UPDATE knowledge_documents SET status='REVOKED', review_note=?, revoked_at=?, "
                        + "updated_by=?, updated_at=? WHERE id=? AND deleted_at IS NULL",
                blankToNull(reason), LocalDateTime.now(), actorUserId, LocalDateTime.now(), documentId);
        auditService.record(actorUserId, "KNOWLEDGE_REVOKE", "knowledge_document", documentId,
                "撤回公共知识文档");
    }

    @Transactional
    public void remove(Long actorUserId, Long documentId) {
        if (findDocument(documentId) == null) {
            throw BusinessException.notFound("知识文档不存在");
        }
        jdbcTemplate.update("UPDATE knowledge_documents SET status='REVOKED', deleted_at=?, "
                        + "updated_by=?, updated_at=? WHERE id=? AND deleted_at IS NULL",
                LocalDateTime.now(), actorUserId, LocalDateTime.now(), documentId);
        auditService.record(actorUserId, "KNOWLEDGE_DELETE", "knowledge_document", documentId,
                "删除公共知识文档");
    }

    @Transactional
    public Long reindex(Long actorUserId, Long documentId) {
        if (findDocument(documentId) == null) {
            throw BusinessException.notFound("知识文档不存在");
        }
        KeyHolder jobKey = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO ingestion_jobs (document_id, job_type, status) VALUES (?, 'REINDEX', 'PENDING')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, documentId);
            return statement;
        }, jobKey);
        Long jobId = generatedId(jobKey);
        auditService.record(actorUserId, "KNOWLEDGE_REINDEX", "knowledge_document", documentId,
                "重建公共知识文档索引");
        scheduleIngestion(jobId);
        return jobId;
    }

    private void scheduleIngestion(Long jobId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ingestionRunner.run(jobId);
                }
            });
        } else {
            ingestionRunner.run(jobId);
        }
    }

    private void transition(Long actorUserId, Long documentId,
                            KnowledgeDocumentStatus from, KnowledgeDocumentStatus to, String action) {
        if (findDocument(documentId) == null) {
            throw BusinessException.notFound("知识文档不存在");
        }
        int updated = jdbcTemplate.update("UPDATE knowledge_documents SET status=?, updated_by=?, updated_at=? "
                        + "WHERE id=? AND status=? AND deleted_at IS NULL",
                to.name(), actorUserId, LocalDateTime.now(), documentId, from.name());
        if (updated == 0) {
            throw BusinessException.conflict("文档当前状态不能执行该操作");
        }
        auditService.record(actorUserId, action, "knowledge_document", documentId,
                "知识文档状态变更为 " + to.name());
    }

    private Map<String, Object> findDocument(Long documentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, source, source_url, file_name, media_type, file_sha256, file_size, "
                        + "version, status, review_note, created_by, updated_by, published_at, revoked_at, "
                        + "created_at, updated_at, deleted_at FROM knowledge_documents WHERE id=? AND deleted_at IS NULL",
                documentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findDocumentWithRawContent(Long documentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, file_name, media_type, raw_content FROM knowledge_documents "
                        + "WHERE id=? AND deleted_at IS NULL", documentId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findBySha256(String sha256) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, status, version, file_name, title FROM knowledge_documents WHERE file_sha256=?",
                sha256);
        return rows.isEmpty() ? null : new LinkedHashMap<>(rows.get(0));
    }

    private Map<String, Object> findJob(Long jobId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, document_id, job_type, status, total_chunks, processed_chunks, error_message, "
                        + "created_at, updated_at, completed_at FROM ingestion_jobs WHERE id=?", jobId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.paramsError("请选择要上传的知识文档");
        }
        if (file.getSize() > properties.getMaxFileBytes()) {
            throw BusinessException.paramsError("知识文档大小不能超过 " + properties.getMaxFileBytes() / 1024 / 1024 + "MB");
        }
        String extension = TextDocumentParser.extension(file.getOriginalFilename());
        if (!List.of("txt", "md", "markdown", "pdf").contains(extension)) {
            throw BusinessException.paramsError("当前支持 TXT、Markdown 和文本型 PDF；OCR 暂未接入");
        }
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw BusinessException.paramsError("知识文档读取失败");
        }
    }

    private String serializeEmbedding(double[] embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("知识片段向量化失败");
        }
    }

    private Long generatedId(KeyHolder keyHolder) {
        Number key = null;
        try {
            key = keyHolder.getKey();
        } catch (org.springframework.dao.InvalidDataAccessApiUsageException ignored) {
            if (!keyHolder.getKeyList().isEmpty()) {
                Map<String, Object> keys = keyHolder.getKeyList().get(0);
                Object id = keys.get("ID");
                if (id == null) {
                    id = keys.get("id");
                }
                if (id instanceof Number number) {
                    key = number;
                }
            }
        }
        if (key == null) {
            throw new IllegalStateException("知识库记录未生成主键");
        }
        return key.longValue();
    }

    private static Long number(Object value) {
        return value instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(value));
    }

    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "knowledge-document";
        }
        String normalized = original.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "_").trim();
        return fileName.isBlank() ? "knowledge-document" : fileName.substring(Math.max(0, fileName.length() - 200));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static String safeError(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "文档处理失败";
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
