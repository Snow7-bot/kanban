package com.kangban.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.AgentExecutionContext;
import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;
import com.kangban.TestCaptchaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通过真实 JDBC 知识库服务执行黄金集评测，不调用外部 Embedding 或大模型服务。
 * 每条样例使用受控候选资料灌入 H2，再经过公共/私有检索服务和权限过滤。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestCaptchaConfig.class)
class JdbcRagGoldenSetEvaluationTest {

    private static final String FIXTURE_SOURCE = "RAG_GOLDEN_REAL_TEST";
    private static final long TEST_USER_ID = 900_000L;
    private static final long OTHER_USER_ID = 900_001L;
    private static final long PRIVATE_RECORD_ID = 2_000_020L;
    private static final long PRIVATE_DOCUMENT_ID = 2_100_020L;
    private static final long PRIVATE_CHUNK_ID = 2_200_020L;
    private static final long SESSION_ID = 2_300_001L;
    private static final LocalDateTime FIXTURE_TIME = LocalDateTime.of(2026, 8, 13, 12, 0);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagProperties properties;

    @Autowired
    private JdbcKnowledgeSearchService publicSearchService;

    @Autowired
    private JdbcPrivateKnowledgeSearchService privateSearchService;

    private final HashEmbeddingClient embeddingClient = new HashEmbeddingClient();
    private GoldenSet goldenSet;
    private Map<String, GoldenDocument> documents;

    @BeforeEach
    void setUp() throws Exception {
        goldenSet = loadGoldenSet();
        documents = goldenSet.documents().stream()
                .collect(Collectors.toMap(GoldenDocument::documentId, Function.identity()));
        clearFixture();
        properties.setMinScore(0.7);
        properties.setTopK(5);
        properties.setMaxContextTokens(6000);
        properties.setEmbeddingModel("local-hash-v1");
    }

    @Test
    void evaluatesGoldenSetThroughJdbcSearchServices() {
        int evidenceCases = 0;
        int noEvidenceCases = 0;
        int accessControlCases = 0;
        int hitAtFive = 0;
        int correctCitations = 0;
        int correctlyEmpty = 0;
        int correctlyDenied = 0;
        List<String> hitFailures = new java.util.ArrayList<>();
        List<String> citationFailures = new java.util.ArrayList<>();
        RagEvaluationStats latencyStats = new RagEvaluationStats();
        Map<String, RagEvaluationStats> categoryLatency = new HashMap<>();

        for (GoldenCase goldenCase : goldenSet.cases()) {
            clearFixture();
            seedPublicCandidates(goldenCase);
            if (goldenCase.category().equals("PRIVATE_RECORD")
                    || goldenCase.category().equals("ACCESS_CONTROL")) {
                seedPrivateDocument();
            }

            AgentExecutionContext context = contextFor(goldenCase);
            long startedAt = System.nanoTime();
            RagSearchResult result;
            try {
                result = RagSearchResult.merge(properties,
                        publicSearchService.search(goldenCase.question()),
                        privateSearchService.search(goldenCase.question(), context));
                latencyStats.recordSuccess(startedAt);
                categoryLatency.computeIfAbsent(goldenCase.category(), key -> new RagEvaluationStats())
                        .recordSuccess(startedAt);
            } catch (RuntimeException exception) {
                latencyStats.recordFailure(startedAt);
                categoryLatency.computeIfAbsent(goldenCase.category(), key -> new RagEvaluationStats())
                        .recordFailure(startedAt);
                throw exception;
            }
            Set<String> expectedCitationIds = expectedCitationIds(goldenCase);

            if (goldenCase.evidenceRequired()) {
                evidenceCases++;
                boolean hitAtFiveCase = result.hits().stream().limit(5)
                        .anyMatch(searchHit -> expectedCitationIds.contains(searchHit.citation().documentId()));
                if (hitAtFiveCase) {
                    hitAtFive++;
                } else {
                    hitFailures.add(goldenCase.id());
                }
                boolean citationCorrect = result.hits().stream()
                        .filter(searchHit -> expectedCitationIds.contains(searchHit.citation().documentId()))
                        .anyMatch(searchHit -> citationMatches(searchHit.citation(), goldenCase));
                if (citationCorrect) {
                    correctCitations++;
                } else {
                    citationFailures.add(goldenCase.id());
                }
            } else if (goldenCase.category().equals("ACCESS_CONTROL")) {
                accessControlCases++;
                if (result.hits().isEmpty()) {
                    correctlyDenied++;
                }
            } else {
                noEvidenceCases++;
                if (result.hits().isEmpty()) {
                    correctlyEmpty++;
                }
            }
        }

        double hitAt5Rate = rate(hitAtFive, evidenceCases);
        double citationAccuracy = rate(correctCitations, evidenceCases);
        double noEvidenceRate = rate(correctlyEmpty, noEvidenceCases);
        double accessControlRate = rate(correctlyDenied, accessControlCases);
        Map<String, Long> categoryCounts = goldenSet.cases().stream()
                .collect(Collectors.groupingBy(GoldenCase::category, HashMap::new, Collectors.counting()));

        System.out.printf("RAG_JDBC_GOLDEN_METRICS cases=%d evidence=%d noEvidence=%d accessControl=%d "
                        + "hitAt5=%.3f citationAccuracy=%.3f noResultRecognition=%.3f "
                        + "accessControlDenial=%.3f latency={%s} categoryLatency=%s "
                        + "categories=%s hitFailures=%s citationFailures=%s%n",
                goldenSet.cases().size(), evidenceCases, noEvidenceCases, accessControlCases,
                hitAt5Rate, citationAccuracy, noEvidenceRate, accessControlRate,
                latencyStats.format(), RagEvaluationStats.formatCategories(categoryLatency),
                categoryCounts, hitFailures, citationFailures);

        assertThat(goldenSet.cases()).hasSize(100);
        assertThat(evidenceCases).isEqualTo(70);
        assertThat(noEvidenceCases).isEqualTo(20);
        assertThat(accessControlCases).isEqualTo(10);
        assertThat(categoryCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PUBLIC_HEALTH", 40L,
                "MEDICATION", 20L,
                "PRIVATE_RECORD", 10L,
                "NO_EVIDENCE", 20L,
                "ACCESS_CONTROL", 10L));
        assertThat(hitAt5Rate).isGreaterThanOrEqualTo(0.95);
        assertThat(citationAccuracy).isEqualTo(1.0);
        assertThat(noEvidenceRate).isEqualTo(1.0);
        assertThat(accessControlRate).isEqualTo(1.0);
    }

    private void seedPublicCandidates(GoldenCase goldenCase) {
        for (String goldenDocumentId : goldenCase.candidateDocumentIds()) {
            if (goldenDocumentId.equals("doc-020")) {
                continue;
            }
            GoldenDocument document = documents.get(goldenDocumentId);
            if (document != null) {
                seedPublicDocument(document);
            }
        }
    }

    private void seedPublicDocument(GoldenDocument document) {
        long documentId = publicDocumentId(document.documentId());
        byte[] content = document.content().getBytes(StandardCharsets.UTF_8);
        String fileHash = String.format("%064d", documentId);
        jdbcTemplate.update("INSERT INTO knowledge_documents "
                        + "(id, title, source, file_name, media_type, file_sha256, file_size, version, status, "
                        + "raw_content, created_by, updated_by, published_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1, 'PUBLISHED', ?, ?, ?, ?, ?, ?)",
                documentId, document.title(), FIXTURE_SOURCE, document.documentId() + ".txt", "text/plain",
                fileHash, content.length, content, TEST_USER_ID, TEST_USER_ID,
                FIXTURE_TIME, FIXTURE_TIME, FIXTURE_TIME);
        jdbcTemplate.update("INSERT INTO knowledge_chunks "
                        + "(id, document_id, chunk_index, page_number, section, content, token_count, "
                        + "embedding_json, embedding_model) VALUES (?, ?, 0, ?, ?, ?, ?, ?, ?)",
                documentId + 10_000_000L, documentId, document.pageNumber(), document.section(),
                document.content(), document.content().length(), embedding(document.content()),
                properties.getEmbeddingModel());
    }

    private void seedPrivateDocument() {
        GoldenDocument document = documents.get("doc-020");
        byte[] content = document.content().getBytes(StandardCharsets.UTF_8);
        jdbcTemplate.update("INSERT INTO medical_records "
                        + "(id, user_id, record_name, status, created_at, updated_at, deleted_at) "
                        + "VALUES (?, ?, ?, 'completed', ?, ?, NULL)",
                PRIVATE_RECORD_ID, TEST_USER_ID, "RAG-GOLDEN-doc-020", FIXTURE_TIME, FIXTURE_TIME);
        jdbcTemplate.update("INSERT INTO family_knowledge_documents "
                        + "(id, medical_record_id, owner_user_id, subject_user_id, family_id, member_id, title, "
                        + "source, version, status, embedding_model, record_updated_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, NULL, NULL, ?, ?, 1, 'READY', ?, ?, ?, ?)",
                PRIVATE_DOCUMENT_ID, PRIVATE_RECORD_ID, TEST_USER_ID, TEST_USER_ID, document.title(),
                FIXTURE_SOURCE, properties.getEmbeddingModel(), FIXTURE_TIME, FIXTURE_TIME, FIXTURE_TIME);
        jdbcTemplate.update("INSERT INTO family_knowledge_chunks "
                        + "(id, document_id, owner_user_id, subject_user_id, family_id, member_id, chunk_index, "
                        + "page_number, section, content, token_count, embedding_json, embedding_model) "
                        + "VALUES (?, ?, ?, ?, NULL, NULL, 0, ?, ?, ?, ?, ?, ?)",
                PRIVATE_CHUNK_ID, PRIVATE_DOCUMENT_ID, TEST_USER_ID, TEST_USER_ID, document.pageNumber(),
                document.section(), document.content(), document.content().length(), embedding(document.content()),
                properties.getEmbeddingModel());
    }

    private Set<String> expectedCitationIds(GoldenCase goldenCase) {
        Set<String> ids = new HashSet<>();
        for (String documentId : goldenCase.expectedDocumentIds()) {
            ids.add(documentId.equals("doc-020")
                    ? "private:" + PRIVATE_DOCUMENT_ID
                    : String.valueOf(publicDocumentId(documentId)));
        }
        return ids;
    }

    private boolean citationMatches(Citation citation, GoldenCase goldenCase) {
        if (goldenCase.expectedDocumentIds().isEmpty()) {
            return false;
        }
        for (String expectedDocumentId : goldenCase.expectedDocumentIds()) {
            GoldenDocument document = documents.get(expectedDocumentId);
            String expectedId = expectedDocumentId.equals("doc-020")
                    ? "private:" + PRIVATE_DOCUMENT_ID
                    : String.valueOf(publicDocumentId(expectedDocumentId));
            String expectedScope = expectedDocumentId.equals("doc-020") ? "PRIVATE" : "PUBLIC";
            if (document != null && expectedId.equals(citation.documentId())
                    && expectedScope.equals(citation.scope())
                    && document.title().equals(citation.title())
                    && java.util.Objects.equals(document.pageNumber(), citation.pageNumber())
                    && document.section().equals(citation.section())) {
                return true;
            }
        }
        return false;
    }

    private AgentExecutionContext contextFor(GoldenCase goldenCase) {
        long actor = goldenCase.category().equals("ACCESS_CONTROL") ? OTHER_USER_ID : TEST_USER_ID;
        long now = System.currentTimeMillis() / 1000;
        return new AgentExecutionContext(actor, TEST_USER_ID, null, SESSION_ID,
                "run-rag-jdbc-" + goldenCase.id(), "trace-rag-jdbc-" + goldenCase.id(),
                now - 1, now + 600);
    }

    private long publicDocumentId(String goldenDocumentId) {
        if (goldenDocumentId.startsWith("doc-")) {
            return 1_000_000L + Long.parseLong(goldenDocumentId.substring(4));
        }
        return switch (goldenDocumentId) {
            case "distractor-a" -> 1_100_001L;
            case "distractor-b" -> 1_100_002L;
            case "distractor-c" -> 1_100_003L;
            default -> throw new IllegalArgumentException("未知黄金文档：" + goldenDocumentId);
        };
    }

    private String embedding(String content) {
        try {
            return objectMapper.writeValueAsString(embeddingClient.embed(content));
        } catch (Exception e) {
            throw new IllegalStateException("测试向量序列化失败", e);
        }
    }

    private void clearFixture() {
        jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE document_id IN "
                + "(SELECT id FROM knowledge_documents WHERE source=?)", FIXTURE_SOURCE);
        jdbcTemplate.update("DELETE FROM knowledge_documents WHERE source=?", FIXTURE_SOURCE);
        jdbcTemplate.update("DELETE FROM family_knowledge_chunks WHERE document_id IN "
                + "(SELECT id FROM family_knowledge_documents WHERE source=?)", FIXTURE_SOURCE);
        jdbcTemplate.update("DELETE FROM family_knowledge_documents WHERE source=?", FIXTURE_SOURCE);
        jdbcTemplate.update("DELETE FROM medical_records WHERE record_name LIKE 'RAG-GOLDEN-%'");
    }

    private GoldenSet loadGoldenSet() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/rag/golden-qa.json")) {
            assertThat(stream).as("golden RAG set resource").isNotNull();
            return objectMapper.readValue(stream, GoldenSet.class);
        }
    }

    private double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    record GoldenSet(String version, String description,
                    List<GoldenDocument> documents, List<GoldenCase> cases) {
    }

    record GoldenDocument(String documentId, String title, Integer pageNumber,
                          String section, String source, String content) {
    }

    record GoldenCase(String id, String question, boolean evidenceRequired,
                      String category, String scope,
                      List<String> expectedDocumentIds,
                      List<String> candidateDocumentIds) {
    }
}
