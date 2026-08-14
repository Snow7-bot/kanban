package com.kangban.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.Citation;
import com.kangban.agent.AgentMetrics;
import com.kangban.agent.RagProperties;
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
public class JdbcKnowledgeSearchService implements KnowledgeSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingClient embeddingClient;
    private final RagProperties properties;
    private final AgentMetrics metrics;

    @Autowired
    public JdbcKnowledgeSearchService(JdbcTemplate jdbcTemplate,
                                      ObjectMapper objectMapper,
                                      EmbeddingClient embeddingClient,
                                      RagProperties properties,
                                      AgentMetrics metrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.metrics = metrics;
    }

    public JdbcKnowledgeSearchService(JdbcTemplate jdbcTemplate,
                                      ObjectMapper objectMapper,
                                      EmbeddingClient embeddingClient,
                                      RagProperties properties) {
        this(jdbcTemplate, objectMapper, embeddingClient, properties, new AgentMetrics());
    }

    @Override
    public RagSearchResult search(String query) {
        if (query == null || query.isBlank()) {
            return RagSearchResult.empty();
        }
        long startedAt = System.currentTimeMillis();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT c.id, c.content, c.embedding_json, c.page_number, c.section, "
                            + "d.id AS document_id, d.title, d.version, d.source, d.updated_at "
                            + "FROM knowledge_chunks c JOIN knowledge_documents d ON d.id=c.document_id "
                            + "WHERE d.status='PUBLISHED' AND d.deleted_at IS NULL AND c.embedding_model=? "
                            + "ORDER BY d.updated_at DESC, c.chunk_index ASC LIMIT 1000",
                    properties.getEmbeddingModel());
            double[] queryVector = embeddingClient.embed(query);
            List<HybridSearchRanker.Candidate> candidates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String content = String.valueOf(row.get("content"));
                String title = String.valueOf(row.get("title"));
                double keywordScore = KeywordRelevance.score(query, title + " " + content);
                double vectorScore = VectorSimilarity.cosine(queryVector, parseEmbedding(row.get("embedding_json")));
                Citation citation = new Citation(
                        String.valueOf(row.get("document_id")),
                        String.valueOf(row.get("title")),
                        String.valueOf(row.get("version")),
                        integer(row.get("page_number")),
                        row.get("section") == null ? "" : String.valueOf(row.get("section")),
                        String.valueOf(row.get("source")),
                        String.valueOf(row.get("updated_at")),
                        "PUBLIC");
                candidates.add(new HybridSearchRanker.Candidate(content, keywordScore, vectorScore, citation));
            }
            List<KnowledgeSearchHit> selected = HybridSearchRanker.rank(candidates, properties);
            metrics.recordRagSearch("public", System.currentTimeMillis() - startedAt, selected.size());
            return new RagSearchResult(buildContext(selected), selected);
        } catch (DataAccessException e) {
            metrics.recordRagFailure("public", System.currentTimeMillis() - startedAt);
            log.warn("Knowledge search failed: errorType={}", e.getClass().getSimpleName());
            throw new RagUnavailableException("公共知识库暂时不可用，请稍后重试。");
        } catch (RuntimeException e) {
            metrics.recordRagFailure("public", System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

    private String buildContext(List<KnowledgeSearchHit> hits) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            KnowledgeSearchHit hit = hits.get(i);
            Citation citation = hit.citation();
            context.append("[资料").append(i + 1).append("] ")
                    .append(citation.title());
            if (citation.pageNumber() != null) {
                context.append("，第").append(citation.pageNumber()).append("页");
            }
            if (citation.section() != null && !citation.section().isBlank()) {
                context.append("，章节：").append(citation.section());
            }
            if (citation.source() != null && !citation.source().isBlank()) {
                context.append("，来源：").append(citation.source());
            }
            context.append("\n").append(hit.content()).append("\n\n");
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
