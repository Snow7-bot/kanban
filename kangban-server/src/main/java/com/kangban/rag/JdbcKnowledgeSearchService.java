package com.kangban.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class JdbcKnowledgeSearchService implements KnowledgeSearchService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EmbeddingClient embeddingClient;
    private final RagProperties properties;

    public JdbcKnowledgeSearchService(JdbcTemplate jdbcTemplate,
                                      ObjectMapper objectMapper,
                                      EmbeddingClient embeddingClient,
                                      RagProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
    }

    @Override
    public RagSearchResult search(String query) {
        if (query == null || query.isBlank()) {
            return RagSearchResult.empty();
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT c.id, c.content, c.embedding_json, c.page_number, c.section, "
                            + "d.id AS document_id, d.title, d.version, d.source, d.updated_at "
                            + "FROM knowledge_chunks c JOIN knowledge_documents d ON d.id=c.document_id "
                            + "WHERE d.status='PUBLISHED' AND d.deleted_at IS NULL "
                            + "ORDER BY d.updated_at DESC, c.chunk_index ASC LIMIT 1000");
            double[] queryVector = embeddingClient.embed(query);
            List<KnowledgeSearchHit> hits = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String content = String.valueOf(row.get("content"));
                double keywordScore = keywordScore(query, content);
                double vectorScore = HashEmbeddingClient.cosine(queryVector, parseEmbedding(row.get("embedding_json")));
                double score = Math.max(keywordScore, vectorScore);
                if (score < properties.getMinScore()) {
                    continue;
                }
                Citation citation = new Citation(
                        String.valueOf(row.get("document_id")),
                        String.valueOf(row.get("title")),
                        String.valueOf(row.get("version")),
                        integer(row.get("page_number")),
                        row.get("section") == null ? "" : String.valueOf(row.get("section")),
                        String.valueOf(row.get("source")),
                        String.valueOf(row.get("updated_at")));
                hits.add(new KnowledgeSearchHit(content, score, citation));
            }
            hits.sort(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed());
            List<KnowledgeSearchHit> selected = selectWithinBudget(hits);
            return new RagSearchResult(buildContext(selected), selected);
        } catch (DataAccessException e) {
            log.warn("Knowledge search failed: errorType={}", e.getClass().getSimpleName());
            throw new RagUnavailableException("公共知识库暂时不可用，请稍后重试。");
        }
    }

    private List<KnowledgeSearchHit> selectWithinBudget(List<KnowledgeSearchHit> hits) {
        List<KnowledgeSearchHit> selected = new ArrayList<>();
        int usedTokens = 0;
        for (KnowledgeSearchHit hit : hits) {
            int tokens = TextChunker.tokenCount(hit.content());
            if (!selected.isEmpty() && usedTokens + tokens > properties.getMaxContextTokens()) {
                break;
            }
            selected.add(hit);
            usedTokens += tokens;
            if (selected.size() >= Math.max(1, properties.getTopK())) {
                break;
            }
        }
        return selected;
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
            return new double[HashEmbeddingClient.DIMENSIONS];
        }
        try {
            return objectMapper.readValue(String.valueOf(raw), new TypeReference<>() {});
        } catch (Exception e) {
            return new double[HashEmbeddingClient.DIMENSIONS];
        }
    }

    private static double keywordScore(String query, String content) {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        String normalizedContent = content.toLowerCase(Locale.ROOT);
        if (normalizedContent.contains(normalizedQuery)) {
            return 1.0;
        }
        Set<Integer> codePoints = new HashSet<>();
        normalizedQuery.codePoints().filter(Character::isLetterOrDigit).forEach(codePoints::add);
        if (codePoints.isEmpty()) {
            return 0.0;
        }
        long matched = codePoints.stream().filter(point -> normalizedContent.indexOf(point) >= 0).count();
        return (double) matched / codePoints.size();
    }

    private static Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }
}
