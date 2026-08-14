package com.kangban.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.TestCaptchaConfig;
import com.kangban.agent.RagProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 显式开启后才运行的真实 Qwen Embedding 评测。
 *
 * <p>默认不参与普通构建。运行前设置 KANGBAN_RUN_LIVE_RAG=true，并通过
 * APP_AI_API_KEY 或 DASHSCOPE_API_KEY 注入密钥；密钥只从进程环境读取，不写入代码、日志或报告。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestCaptchaConfig.class)
@EnabledIfEnvironmentVariable(named = "KANGBAN_RUN_LIVE_RAG", matches = "true")
class QwenLiveRagGoldenSetEvaluationTest {

    private static final String FIXTURE_SOURCE = "RAG_LIVE_TEST";
    private static final long ACTOR_USER_ID = 910_000L;

    @Autowired
    private KnowledgeDocumentService documentService;

    @Autowired
    private KnowledgeSearchService searchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagProperties properties;

    @MockitoBean
    private KnowledgeIngestionRunner ingestionRunner;

    private LiveGoldenSet goldenSet;
    private Map<String, Long> documentIds;
    private RagEvaluationStats ingestionStats;

    @DynamicPropertySource
    static void qwenProperties(DynamicPropertyRegistry registry) {
        String apiKey = firstNonBlank(System.getenv("APP_AI_API_KEY"),
                System.getenv("DASHSCOPE_API_KEY"));
        if (apiKey == null) {
            fail("KANGBAN_RUN_LIVE_RAG=true 时必须通过环境变量提供 APP_AI_API_KEY 或 DASHSCOPE_API_KEY");
        }
        registry.add("app.ai.provider", () -> "qwen");
        registry.add("app.ai.api-key", () -> apiKey);
        registry.add("app.agent.rag.enabled", () -> "true");
        registry.add("app.agent.rag.embedding-provider", () -> "qwen");
        registry.add("app.agent.rag.embedding-api-url", () -> envOrDefault(
                "APP_RAG_EMBEDDING_API_URL",
                "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"));
        registry.add("app.agent.rag.embedding-model", () -> envOrDefault(
                "APP_RAG_EMBEDDING_MODEL", "text-embedding-v4"));
        registry.add("app.agent.rag.embedding-dimensions", () -> envOrDefault(
                "APP_RAG_EMBEDDING_DIMENSIONS", "1024"));
        registry.add("app.agent.rag.embedding-batch-size", () -> envOrDefault(
                "APP_RAG_EMBEDDING_BATCH_SIZE", "16"));
    }

    @BeforeEach
    void setUp() throws Exception {
        goldenSet = loadGoldenSet();
        clearFixture();
        properties.setMinScore(0.7);
        properties.setTopK(5);
        properties.setMaxContextTokens(6000);
        documentIds = new HashMap<>();
        ingestionStats = new RagEvaluationStats();
        for (LiveDocumentCase testCase : goldenSet.cases()) {
            if (testCase.fileName() == null || testCase.fileName().isBlank()) {
                continue;
            }
            if (documentIds.containsKey(testCase.fileName())) {
                continue;
            }
            String resource = "/rag/live-documents/" + testCase.fileName();
            byte[] bytes;
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertThat(input).as("live RAG fixture " + resource).isNotNull();
                bytes = input.readAllBytes();
            }
            long startedAt = System.nanoTime();
            try {
                Map<String, Object> created = documentService.upload(
                        ACTOR_USER_ID, testCase.expectedTitle(), FIXTURE_SOURCE, null,
                        new MockMultipartFile("file", testCase.fileName(), "text/markdown", bytes));
                Long documentId = ((Number) created.get("documentId")).longValue();
                documentIds.put(testCase.fileName(), documentId);
                documentService.processJobNow(((Number) created.get("jobId")).longValue());
                assertThat(documentService.getJob(((Number) created.get("jobId")).longValue()).get("status"))
                        .isEqualTo("SUCCEEDED");
                documentService.submitReview(ACTOR_USER_ID, documentId);
                documentService.publish(ACTOR_USER_ID, documentId, "真实 Embedding 评测资料");
                ingestionStats.recordSuccess(startedAt);
            } catch (RuntimeException exception) {
                ingestionStats.recordFailure(startedAt);
                throw exception;
            }
        }
    }

    @AfterEach
    void tearDown() {
        clearFixture();
    }

    @Test
    void evaluatesRealFilesThroughQwenEmbeddingAndJdbcSearch() {
        int evidenceCases = 0;
        int noEvidenceCases = 0;
        int hitAtFive = 0;
        int correctCitations = 0;
        int correctlyEmpty = 0;
        List<String> hitFailures = new java.util.ArrayList<>();
        List<String> citationFailures = new java.util.ArrayList<>();
        RagEvaluationStats searchStats = new RagEvaluationStats();
        Map<String, RagEvaluationStats> categoryLatency = new HashMap<>();
        for (LiveDocumentCase testCase : goldenSet.cases()) {
            long startedAt = System.nanoTime();
            RagSearchResult result;
            String category = testCase.evidenceRequired() ? "EVIDENCE" : "NO_EVIDENCE";
            try {
                result = searchService.search(testCase.question());
                searchStats.recordSuccess(startedAt);
                categoryLatency.computeIfAbsent(category, key -> new RagEvaluationStats())
                        .recordSuccess(startedAt);
            } catch (RuntimeException exception) {
                searchStats.recordFailure(startedAt);
                categoryLatency.computeIfAbsent(category, key -> new RagEvaluationStats())
                        .recordFailure(startedAt);
                throw exception;
            }
            if (testCase.evidenceRequired()) {
                evidenceCases++;
                Long expectedId = documentIds.get(testCase.fileName());
                boolean hit = result.hits().stream().limit(5)
                        .anyMatch(searchHit -> String.valueOf(expectedId).equals(searchHit.citation().documentId()));
                if (hit) {
                    hitAtFive++;
                } else {
                    hitFailures.add(testCase.id());
                }
                boolean citationCorrect = result.hits().stream()
                        .filter(searchHit -> String.valueOf(expectedId).equals(searchHit.citation().documentId()))
                        .anyMatch(searchHit -> testCase.expectedTitle().equals(searchHit.citation().title())
                                && "PUBLIC".equals(searchHit.citation().scope())
                                && searchHit.citation().section() != null
                                && !searchHit.citation().section().isBlank());
                if (citationCorrect) {
                    correctCitations++;
                } else {
                    citationFailures.add(testCase.id());
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
        System.out.printf("RAG_QWEN_LIVE_METRICS cases=%d evidence=%d noEvidence=%d "
                        + "hitAt5=%.3f citationAccuracy=%.3f noResultRecognition=%.3f "
                        + "embeddingModel=%s embeddingDimensions=%d ingestionLatency={%s} "
                        + "searchLatency={%s} categoryLatency=%s hitFailures=%s citationFailures=%s%n",
                goldenSet.cases().size(), evidenceCases, noEvidenceCases,
                hitAt5Rate, citationAccuracy, noEvidenceRate,
                properties.getEmbeddingModel(), properties.getEmbeddingDimensions(),
                ingestionStats.format(), searchStats.format(),
                RagEvaluationStats.formatCategories(categoryLatency),
                hitFailures, citationFailures);

        assertThat(hitAt5Rate).isGreaterThanOrEqualTo(0.80);
        assertThat(citationAccuracy).isGreaterThanOrEqualTo(0.95);
        assertThat(noEvidenceRate).isGreaterThanOrEqualTo(0.50);
    }

    private LiveGoldenSet loadGoldenSet() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/rag/live-golden-qa.json")) {
            assertThat(input).as("live RAG golden set").isNotNull();
            return objectMapper.readValue(input, LiveGoldenSet.class);
        }
    }

    private void clearFixture() {
        jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE document_id IN "
                + "(SELECT id FROM knowledge_documents WHERE source=?)", FIXTURE_SOURCE);
        jdbcTemplate.update("DELETE FROM ingestion_jobs WHERE document_id IN "
                + "(SELECT id FROM knowledge_documents WHERE source=?)", FIXTURE_SOURCE);
        jdbcTemplate.update("DELETE FROM knowledge_documents WHERE source=?", FIXTURE_SOURCE);
        jdbcTemplate.update("DELETE FROM audit_logs WHERE detail LIKE '%真实 Embedding 评测资料%'");
    }

    private double rate(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second != null && !second.isBlank() ? second : null;
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    record LiveGoldenSet(String version, String description, List<LiveDocumentCase> cases) {
    }

    record LiveDocumentCase(String id, String question, String fileName,
                            String expectedTitle, boolean evidenceRequired) {
    }
}
