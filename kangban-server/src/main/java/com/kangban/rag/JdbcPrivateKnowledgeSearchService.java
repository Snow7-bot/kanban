package com.kangban.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.AgentExecutionContext;
import com.kangban.agent.AgentMetrics;
import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;
import com.kangban.common.BusinessException;
import com.kangban.service.FamilyAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class JdbcPrivateKnowledgeSearchService implements PrivateKnowledgeSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingClient embeddingClient;
    private final RagProperties properties;
    private final FamilyAccessService familyAccessService;
    private final AgentMetrics metrics;

    @Autowired
    public JdbcPrivateKnowledgeSearchService(JdbcTemplate jdbcTemplate,
                                             ObjectMapper objectMapper,
                                             EmbeddingClient embeddingClient,
                                             RagProperties properties,
                                             FamilyAccessService familyAccessService,
                                             AgentMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.familyAccessService = familyAccessService;
        this.metrics = metrics;
    }

    public JdbcPrivateKnowledgeSearchService(JdbcTemplate jdbcTemplate,
                                             ObjectMapper objectMapper,
                                             EmbeddingClient embeddingClient,
                                             RagProperties properties,
                                             FamilyAccessService familyAccessService) {
        this(jdbcTemplate, objectMapper, embeddingClient, properties, familyAccessService, new AgentMetrics());
    }

    @Override
    public RagSearchResult search(String query, AgentExecutionContext context) {
        if (query == null || query.isBlank() || context == null || !hasRecordPermission(context)) {
            return RagSearchResult.empty();
        }
        long startedAt = System.currentTimeMillis();
        try {
            String memberFilter = context.memberId() == null
                    ? " AND d.member_id IS NULL " : " AND d.member_id=? ";
            String sql = "SELECT c.content, c.embedding_json, c.page_number, c.section, "
                    + "d.id AS document_id, d.title, d.version, d.source, d.updated_at "
                    + "FROM family_knowledge_chunks c "
                    + "JOIN family_knowledge_documents d ON d.id=c.document_id "
                    + "JOIN medical_records m ON m.id=d.medical_record_id "
                    + "WHERE d.status='READY' AND d.deleted_at IS NULL AND d.revoked_at IS NULL "
                    + "AND m.deleted_at IS NULL AND c.embedding_model=? "
                    + "AND d.owner_user_id=? AND d.subject_user_id=?"
                    + memberFilter
                    + " ORDER BY d.updated_at DESC, c.chunk_index ASC LIMIT 1000";
            List<Object> arguments = new ArrayList<>(List.of(
                    properties.getEmbeddingModel(), context.subjectUserId(), context.subjectUserId()));
            if (context.memberId() != null) {
                arguments.add(context.memberId());
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, arguments.toArray());
            double[] queryVector = embeddingClient.embed(query);
            List<HybridSearchRanker.Candidate> candidates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String content = String.valueOf(row.get("content"));
                String title = String.valueOf(row.get("title"));
                double keywordScore = KeywordRelevance.score(query, title + " " + content);
                double vectorScore = VectorSimilarity.cosine(queryVector, parseEmbedding(row.get("embedding_json")));
                Citation citation = new Citation(
                        "private:" + row.get("document_id"), String.valueOf(row.get("title")),
                        String.valueOf(row.get("version")), integer(row.get("page_number")),
                        row.get("section") == null ? "" : String.valueOf(row.get("section")),
                        String.valueOf(row.get("source")), String.valueOf(row.get("updated_at")), "PRIVATE");
                candidates.add(new HybridSearchRanker.Candidate(content, keywordScore, vectorScore, citation));
            }
            List<KnowledgeSearchHit> selected = HybridSearchRanker.rank(candidates, properties);
            metrics.recordRagSearch("private", System.currentTimeMillis() - startedAt, selected.size());
            return new RagSearchResult(context(selected), selected);
        } catch (DataAccessException e) {
            metrics.recordRagFailure("private", System.currentTimeMillis() - startedAt);
            log.warn("Private knowledge search failed: errorType={}", e.getClass().getSimpleName());
            throw new RagUnavailableException("家庭私有病历知识库暂时不可用，请稍后重试。");
        } catch (RuntimeException e) {
            metrics.recordRagFailure("private", System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

    private boolean hasRecordPermission(AgentExecutionContext context) {
        if (context.actorUserId().equals(context.subjectUserId())) {
            return true;
        }
        try {
            familyAccessService.require(context.actorUserId(), context.subjectUserId(),
                    FamilyAccessService.Scope.VIEW_RECORDS);
            return true;
        } catch (BusinessException e) {
            log.info("Private knowledge scope denied: actorUserId={}, subjectUserId={}",
                    context.actorUserId(), context.subjectUserId());
            return false;
        }
    }

    private String context(List<KnowledgeSearchHit> hits) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Citation citation = hits.get(i).citation();
            context.append("[资料").append(i + 1).append("] ").append(citation.title())
                    .append("，家庭私有病历，来源：").append(citation.source()).append("\n")
                    .append(hits.get(i).content()).append("\n\n");
        }
        return context.toString().trim();
    }

    private double[] parseEmbedding(Object raw) {
        if (raw == null) {
            return new double[embeddingDimensions()];
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception e) {
            return new double[embeddingDimensions()];
        }
    }

    private int embeddingDimensions() {
        return embeddingClient.dimensions() > 0 ? embeddingClient.dimensions() : HashEmbeddingClient.DIMENSIONS;
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
