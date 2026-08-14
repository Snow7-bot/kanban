package com.kangban.rag;

import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagSearchResultTest {

    @Test
    void mergeRenumbersEvidenceAndKeepsPublicPrivateScopes() {
        Citation publicCitation = new Citation("1", "公共指南", "1", null,
                "概述", "官方资料", "2026-08-12", "PUBLIC");
        Citation privateCitation = new Citation("private:2", "我的病历", "1", null,
                "OCR文本", "家庭私有病历", "2026-08-12", "PRIVATE");
        RagProperties properties = new RagProperties();
        properties.setTopK(5);

        RagSearchResult result = RagSearchResult.merge(properties,
                new RagSearchResult("", List.of(new KnowledgeSearchHit("公共内容", 0.8, publicCitation))),
                new RagSearchResult("", List.of(new KnowledgeSearchHit("私有内容", 1.0, privateCitation))));

        assertThat(result.context()).contains("[资料1] 我的病历", "[资料2] 公共指南");
        assertThat(result.citations()).extracting(com.kangban.agent.Citation::scope)
                .containsExactly("PRIVATE", "PUBLIC");
    }

    @Test
    void mergeRemovesSameChunkReturnedByPublicAndPrivateBranches() {
        Citation citation = new Citation("1", "同一资料", "1", null,
                "概述", "测试资料", "2026-08-12", "PUBLIC");
        RagProperties properties = new RagProperties();
        properties.setTopK(5);

        RagSearchResult result = RagSearchResult.merge(properties,
                new RagSearchResult("", List.of(new KnowledgeSearchHit("同一段内容", 0.9, citation))),
                new RagSearchResult("", List.of(new KnowledgeSearchHit("同一 段内容", 0.8, citation))));

        assertThat(result.hits()).hasSize(1);
        assertThat(result.context()).contains("[资料1]").doesNotContain("[资料2]");
    }
}
