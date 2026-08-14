package com.kangban.rag;

import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchRankerTest {

    @Test
    void exactMedicalTermOutranksSemanticOnlyCandidate() {
        RagProperties properties = new RagProperties();
        properties.setMinScore(0.7);
        properties.setTopK(2);

        List<KnowledgeSearchHit> hits = HybridSearchRanker.rank(List.of(
                candidate("阿司匹林每日一次，饭后服用。", 1.0, 0.20, "1"),
                candidate("抗血小板药物需要遵医嘱使用。", 0.0, 0.95, "2")
        ), properties);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).citation().documentId()).isEqualTo("1");
        assertThat(hits.get(0).score()).isEqualTo(1.0);
    }

    @Test
    void removesDuplicateChunksAndStopsAtContextBudget() {
        RagProperties properties = new RagProperties();
        properties.setMinScore(0.0);
        properties.setTopK(5);
        properties.setMaxContextTokens(4);

        List<KnowledgeSearchHit> hits = HybridSearchRanker.rank(List.of(
                candidate("重复片段内容", 1.0, 0.90, "1"),
                candidate("重复 片段 内容", 0.9, 0.80, "1"),
                candidate("第二个片段内容", 0.8, 0.70, "2")
        ), properties);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).content()).isEqualTo("重复片段内容");
    }

    private HybridSearchRanker.Candidate candidate(String content, double keywordScore,
                                                    double vectorScore, String documentId) {
        Citation citation = new Citation(documentId, "资料" + documentId, "1", null,
                "章节", "测试资料", "2026-08-12", "PUBLIC");
        return new HybridSearchRanker.Candidate(content, keywordScore, vectorScore, citation);
    }
}
