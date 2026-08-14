package com.kangban.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.RagProperties;
import com.kangban.entity.MedicalRecord;
import com.kangban.mapper.MedicalRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 * 将已有 OCR 结果建立为家庭私有索引。这里不负责 OCR，也不把答案写回病历。
 */
@Service
@RequiredArgsConstructor
public class PrivateKnowledgeIndexService {

    private final JdbcTemplate jdbcTemplate;
    private final MedicalRecordMapper medicalRecordMapper;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;
    private final TextChunker textChunker;
    private final RagProperties properties;

    @Transactional
    public void indexCompletedRecord(Long recordId) {
        MedicalRecord record = medicalRecordMapper.selectOne(new LambdaQueryWrapper<MedicalRecord>()
                .eq(MedicalRecord::getId, recordId));
        if (record == null || record.getDeletedAt() != null
                || !"completed".equalsIgnoreCase(record.getStatus())
                || sourceText(record).isBlank()) {
            revoke(recordId, record == null ? null : record.getUserId(), "MEDICAL_RECORD_NOT_INDEXABLE");
            return;
        }

        Long documentId = findDocumentId(recordId);
        Long familyId = findFamilyId(record.getUserId());
        if (documentId == null) {
            documentId = insertDocument(record, familyId);
        } else {
            updateDocument(documentId, record, familyId);
            jdbcTemplate.update("DELETE FROM family_knowledge_chunks WHERE document_id=?", documentId);
        }

        ParsedDocument document = new ParsedDocument(List.of(new ParsedPage(null, "OCR文本", sourceText(record))));
        List<KnowledgeChunkDraft> chunks = textChunker.chunk(document);
        List<double[]> embeddings = embeddingClient.embedBatch(
                chunks.stream().map(KnowledgeChunkDraft::content).toList());
        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException("私有病历向量数量不一致");
        }
        for (int index = 0; index < chunks.size(); index++) {
            KnowledgeChunkDraft chunk = chunks.get(index);
            jdbcTemplate.update(
                    "INSERT INTO family_knowledge_chunks "
                            + "(document_id, owner_user_id, subject_user_id, family_id, member_id, "
                            + "chunk_index, page_number, section, content, token_count, embedding_json, embedding_model) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    documentId, record.getUserId(), record.getUserId(), null, record.getMemberId(),
                    chunk.chunkIndex(), chunk.pageNumber(), chunk.section(), chunk.content(), chunk.tokenCount(),
                    toJson(embeddings.get(index)), properties.getEmbeddingModel());
        }
        jdbcTemplate.update("UPDATE family_knowledge_chunks SET family_id=? WHERE document_id=?", familyId, documentId);
        markEvent(record, "MEDICAL_RECORD_UPSERT", "SUCCEEDED", null, familyId);
    }

    @Transactional
    public void revokeRecord(Long recordId, Long ownerUserId) {
        revoke(recordId, ownerUserId, "MEDICAL_RECORD_REVOKE");
    }

    /**
     * 撤销病历时同时清理向量正文；文档保留 REVOKED 记录用于审计和幂等恢复判断。
     */

    private void revoke(Long recordId, Long ownerUserId, String eventType) {
        if (recordId == null) {
            return;
        }
        List<Long> documentIds = jdbcTemplate.query(
                "SELECT id FROM family_knowledge_documents WHERE medical_record_id=?",
                (rs, rowNum) -> rs.getLong(1), recordId);
        jdbcTemplate.update("UPDATE family_knowledge_documents "
                        + "SET status='REVOKED', revoked_at=COALESCE(revoked_at, CURRENT_TIMESTAMP), "
                        + "deleted_at=COALESCE(deleted_at, CURRENT_TIMESTAMP), updated_at=CURRENT_TIMESTAMP "
                        + "WHERE medical_record_id=?", recordId);
        for (Long documentId : documentIds) {
            jdbcTemplate.update("DELETE FROM family_knowledge_chunks WHERE document_id=?", documentId);
        }
        if (ownerUserId != null) {
            MedicalRecord eventRecord = new MedicalRecord();
            eventRecord.setId(recordId);
            eventRecord.setUserId(ownerUserId);
            eventRecord.setMemberId(null);
            markEvent(eventRecord, eventType, "SUCCEEDED", null, findFamilyId(ownerUserId));
        }
    }

    private Long findDocumentId(Long recordId) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM family_knowledge_documents WHERE medical_record_id=?",
                (rs, rowNum) -> rs.getLong(1), recordId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long insertDocument(MedicalRecord record, Long familyId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO family_knowledge_documents "
                            + "(medical_record_id, owner_user_id, subject_user_id, family_id, member_id, title, "
                            + "source, version, status, embedding_model, record_updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'READY', ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, record.getId());
            statement.setLong(2, record.getUserId());
            statement.setLong(3, record.getUserId());
            statement.setObject(4, familyId);
            statement.setObject(5, record.getMemberId());
            statement.setString(6, safeTitle(record));
            statement.setString(7, "家庭私有病历");
            statement.setString(8, properties.getEmbeddingModel());
            statement.setTimestamp(9, timestamp(record.getUpdatedAt()));
            return statement;
        }, keyHolder);
        Map<String, Object> generatedKeys = keyHolder.getKeys();
        Number generatedId = generatedKeys.entrySet().stream()
                .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .findFirst()
                .orElseGet(() -> generatedKeys.values().stream()
                        .filter(Number.class::isInstance)
                        .map(Number.class::cast)
                        .findFirst()
                        .orElse(null));
        return Objects.requireNonNull(generatedId).longValue();
    }

    private void updateDocument(Long documentId, MedicalRecord record, Long familyId) {
        jdbcTemplate.update("UPDATE family_knowledge_documents SET owner_user_id=?, subject_user_id=?, "
                        + "family_id=?, "
                        + "member_id=?, title=?, source='家庭私有病历', version=version+1, status='READY', "
                        + "embedding_model=?, record_updated_at=?, revoked_at=NULL, deleted_at=NULL, "
                        + "updated_at=CURRENT_TIMESTAMP WHERE id=?",
                record.getUserId(), record.getUserId(), familyId, record.getMemberId(), safeTitle(record),
                properties.getEmbeddingModel(), timestamp(record.getUpdatedAt()), documentId);
    }

    private void markEvent(MedicalRecord record, String eventType, String status, String error, Long familyId) {
        String eventKey = eventType + ":" + record.getId();
        int updated = jdbcTemplate.update("UPDATE knowledge_outbox_events SET event_type=?, status=?, "
                        + "attempts=attempts+1, last_error=?, processed_at=CURRENT_TIMESTAMP "
                        + "WHERE event_key=?",
                eventType, status, error, eventKey);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO knowledge_outbox_events "
                            + "(event_key, event_type, medical_record_id, owner_user_id, subject_user_id, family_id, member_id, status, attempts, last_error, processed_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP)",
                    eventKey, eventType, record.getId(), record.getUserId(), record.getUserId(), familyId, record.getMemberId(),
                    status, error);
        }
    }

    private Long findFamilyId(Long userId) {
        if (userId == null) {
            return null;
        }
        List<Long> familyIds = jdbcTemplate.query(
                "SELECT family_id FROM family_group_members WHERE user_id=? AND status='active' ORDER BY family_id LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), userId);
        return familyIds.isEmpty() ? null : familyIds.get(0);
    }

    private String sourceText(MedicalRecord record) {
        return String.join("\n\n",
                        nonBlank(record.getOcrText()),
                        nonBlank(record.getDiagnosisData()),
                        nonBlank(record.getMedicationsData()),
                        nonBlank(record.getAdvicesData()))
                .trim();
    }

    private String safeTitle(MedicalRecord record) {
        return record.getRecordName() == null || record.getRecordName().isBlank()
                ? "病历记录" : record.getRecordName();
    }

    private String nonBlank(String value) {
        return value == null ? "" : value.trim();
    }

    private Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("私有病历向量序列化失败", e);
        }
    }
}
