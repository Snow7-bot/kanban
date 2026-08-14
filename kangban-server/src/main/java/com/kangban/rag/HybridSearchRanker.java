package com.kangban.rag;

import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 关键词、向量与 RRF 融合的确定性检索排序器。 */
final class HybridSearchRanker {

    private static final int RRF_K = 60;
    private static final double VECTOR_WEIGHT = 0.55;
    private static final double KEYWORD_WEIGHT = 0.25;
    private static final double RRF_WEIGHT = 0.20;

    private HybridSearchRanker() {
    }

    static List<KnowledgeSearchHit> rank(List<Candidate> candidates, RagProperties properties) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<Integer> vectorOrder = order(candidates, Comparator.comparingDouble(Candidate::vectorScore)
                .reversed().thenComparing(Comparator.comparingDouble(Candidate::keywordScore).reversed()));
        List<Integer> keywordOrder = order(candidates, Comparator.comparingDouble(Candidate::keywordScore)
                .reversed().thenComparing(Comparator.comparingDouble(Candidate::vectorScore).reversed()));
        int[] vectorRanks = ranks(vectorOrder, candidates.size());
        int[] keywordRanks = ranks(keywordOrder, candidates.size());

        double[] rrfScores = new double[candidates.size()];
        double maxRrf = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            rrfScores[i] = 1.0 / (RRF_K + vectorRanks[i]) + 1.0 / (RRF_K + keywordRanks[i]);
            maxRrf = Math.max(maxRrf, rrfScores[i]);
        }

        List<ScoredCandidate> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            double keywordScore = bounded(candidate.keywordScore());
            double vectorScore = bounded(candidate.vectorScore());
            double fusedScore = VECTOR_WEIGHT * vectorScore
                    + KEYWORD_WEIGHT * keywordScore
                    + RRF_WEIGHT * (maxRrf == 0.0 ? 0.0 : rrfScores[i] / maxRrf);
            // 医疗术语的精确命中不能被不同向量模型的分数尺度压低。
            double finalScore = Math.max(keywordScore, fusedScore);
            if (finalScore >= properties.getMinScore()) {
                scored.add(new ScoredCandidate(candidate, finalScore, vectorScore, keywordScore));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::keywordScore).reversed())
                .thenComparing(Comparator.comparingDouble(ScoredCandidate::vectorScore).reversed()));

        List<KnowledgeSearchHit> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int usedTokens = 0;
        for (ScoredCandidate item : scored) {
            Candidate candidate = item.candidate();
            if (!seen.add(dedupeKey(candidate.content(), candidate.citation()))) {
                continue;
            }
            int tokens = TextChunker.tokenCount(candidate.content());
            if (!selected.isEmpty() && usedTokens + tokens > properties.getMaxContextTokens()) {
                break;
            }
            selected.add(new KnowledgeSearchHit(candidate.content(), item.score(), candidate.citation()));
            usedTokens += tokens;
            if (selected.size() >= Math.max(1, properties.getTopK())) {
                break;
            }
        }
        return selected;
    }

    private static List<Integer> order(List<Candidate> candidates, Comparator<Candidate> comparator) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            order.add(i);
        }
        order.sort((left, right) -> comparator.compare(candidates.get(left), candidates.get(right)));
        return order;
    }

    private static int[] ranks(List<Integer> order, int size) {
        int[] ranks = new int[size];
        for (int i = 0; i < order.size(); i++) {
            ranks[order.get(i)] = i + 1;
        }
        return ranks;
    }

    private static double bounded(double score) {
        return Double.isFinite(score) ? Math.max(0.0, Math.min(1.0, score)) : 0.0;
    }

    private static String dedupeKey(String content, Citation citation) {
        String normalizedContent = content == null ? "" : content.replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        return citation.scope() + "|" + citation.documentId() + "|" + normalizedContent;
    }

    record Candidate(String content, double keywordScore, double vectorScore, Citation citation) {
    }

    private record ScoredCandidate(Candidate candidate, double score, double vectorScore, double keywordScore) {
    }
}
