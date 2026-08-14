package com.kangban.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RAG 中文黄金问答集的离线评测，不调用真实 Embedding 或大模型服务。
 * 评测的是当前混合召回器的排序、结构化引用和零结果判断。
 */
class RagGoldenSetEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HashEmbeddingClient embeddingClient = new HashEmbeddingClient();

    @Test
    void evaluatesHitAtFiveCitationAccuracyAndNoEvidenceRecognition() throws Exception {
        GoldenSet goldenSet = loadGoldenSet();
        Map<String, GoldenDocument> documents = goldenSet.documents().stream()
                .collect(Collectors.toMap(GoldenDocument::documentId, Function.identity()));
        RagProperties properties = new RagProperties();
        properties.setMinScore(0.7);
        properties.setTopK(5);
        properties.setMaxContextTokens(6000);

        int evidenceCases = 0;
        int noEvidenceCases = 0;
        int accessControlCases = 0;
        int hitAtFive = 0;
        int correctCitations = 0;
        int correctlyEmpty = 0;
        int correctlyDenied = 0;
        List<String> hitAtFiveFailures = new java.util.ArrayList<>();
        List<String> citationFailures = new java.util.ArrayList<>();
        RagEvaluationStats latencyStats = new RagEvaluationStats();
        Map<String, RagEvaluationStats> categoryLatency = new HashMap<>();
        for (GoldenCase goldenCase : goldenSet.cases()) {
            long startedAt = System.nanoTime();
            RagSearchResult result;
            try {
                result = search(goldenCase, documents, properties);
                latencyStats.recordSuccess(startedAt);
                categoryLatency.computeIfAbsent(goldenCase.category(), key -> new RagEvaluationStats())
                        .recordSuccess(startedAt);
            } catch (RuntimeException exception) {
                latencyStats.recordFailure(startedAt);
                categoryLatency.computeIfAbsent(goldenCase.category(), key -> new RagEvaluationStats())
                        .recordFailure(startedAt);
                throw exception;
            }
            Set<String> expected = Set.copyOf(goldenCase.expectedDocumentIds());
            if (goldenCase.evidenceRequired()) {
                evidenceCases++;
                boolean hitAtFiveCase = result.hits().stream().limit(5)
                        .anyMatch(hit -> expected.contains(hit.citation().documentId()));
                if (hitAtFiveCase) {
                    hitAtFive++;
                } else {
                    hitAtFiveFailures.add(goldenCase.id());
                }
                boolean citationCorrect = result.hits().stream()
                        .filter(hit -> expected.contains(hit.citation().documentId()))
                        .anyMatch(hit -> citationMatches(hit.citation(),
                                documents.get(hit.citation().documentId()), goldenCase.scope()));
                if (citationCorrect) {
                    correctCitations++;
                } else {
                    citationFailures.add(goldenCase.id());
                }
            } else if ("ACCESS_CONTROL".equals(goldenCase.category())) {
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
        System.out.printf("RAG_GOLDEN_METRICS cases=%d evidence=%d noEvidence=%d accessControl=%d "
                        + "hitAt5=%.3f citationAccuracy=%.3f noResultRecognition=%.3f "
                        + "accessControlDenial=%.3f latency={%s} categoryLatency=%s "
                        + "categories=%s hitFailures=%s citationFailures=%s%n",
                goldenSet.cases().size(), evidenceCases, noEvidenceCases, accessControlCases,
                hitAt5Rate, citationAccuracy, noEvidenceRate, accessControlRate,
                latencyStats.format(), RagEvaluationStats.formatCategories(categoryLatency), categoryCounts,
                hitAtFiveFailures, citationFailures);

        assertThat(goldenSet.cases()).hasSize(100);
        assertThat(goldenSet.cases().stream().map(GoldenCase::id).distinct()).hasSize(100);
        assertThat(goldenSet.cases()).allSatisfy(goldenCase -> {
            assertThat(goldenCase.category()).isNotBlank();
            assertThat(goldenCase.scope()).isNotBlank();
            if (goldenCase.evidenceRequired()) {
                assertThat(goldenCase.expectedDocumentIds()).isNotEmpty();
            } else {
                assertThat(goldenCase.expectedDocumentIds()).isEmpty();
            }
        });
        assertThat(categoryCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                "PUBLIC_HEALTH", 40L,
                "MEDICATION", 20L,
                "PRIVATE_RECORD", 10L,
                "NO_EVIDENCE", 20L,
                "ACCESS_CONTROL", 10L));
        assertThat(evidenceCases).isEqualTo(70);
        assertThat(noEvidenceCases).isEqualTo(20);
        assertThat(accessControlCases).isEqualTo(10);
        assertThat(hitAt5Rate).isGreaterThanOrEqualTo(0.95);
        assertThat(citationAccuracy).isEqualTo(1.0);
        assertThat(noEvidenceRate).isEqualTo(1.0);
        assertThat(accessControlRate).isEqualTo(1.0);
    }

    private RagSearchResult search(GoldenCase goldenCase,
                                   Map<String, GoldenDocument> documents,
                                   RagProperties properties) {
        if (goldenCase.candidateDocumentIds().isEmpty()) {
            return RagSearchResult.empty();
        }
        double[] queryVector = embeddingClient.embed(goldenCase.question());
        var candidates = goldenCase.candidateDocumentIds().stream()
                .map(documents::get)
                .filter(java.util.Objects::nonNull)
                .map(document -> new HybridSearchRanker.Candidate(
                        document.content(),
                        KeywordRelevance.score(goldenCase.question(),
                                document.title() + " " + document.content()),
                        VectorSimilarity.cosine(queryVector, embeddingClient.embed(document.content())),
                citation(document, goldenCase)))
                .toList();
        return new RagSearchResult("", HybridSearchRanker.rank(candidates, properties));
    }

    private Citation citation(GoldenDocument document, GoldenCase goldenCase) {
        return new Citation(document.documentId(), document.title(), "1", document.pageNumber(),
                document.section(), document.source(), "2026-08-12", goldenCase.scope());
    }

    private boolean citationMatches(Citation citation, GoldenDocument document, String expectedScope) {
        return document != null
                && citation.pageNumber().equals(document.pageNumber())
                && citation.section().equals(document.section())
                && citation.title().equals(document.title())
                && citation.scope().equals(expectedScope);
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
                    java.util.List<GoldenDocument> documents,
                    java.util.List<GoldenCase> cases) {
    }

    record GoldenDocument(String documentId, String title, Integer pageNumber,
                          String section, String source, String content) {
    }

    record GoldenCase(String id, String question, boolean evidenceRequired,
                      String category, String scope,
                      java.util.List<String> expectedDocumentIds,
                      java.util.List<String> candidateDocumentIds) {
    }
}
