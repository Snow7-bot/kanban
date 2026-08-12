package com.kangban.rag;

import com.kangban.agent.Citation;

import java.util.List;

public record RagSearchResult(String context, List<KnowledgeSearchHit> hits) {
    public RagSearchResult {
        context = context == null ? "" : context;
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public static RagSearchResult empty() {
        return new RagSearchResult("", List.of());
    }

    public List<Citation> citations() {
        return hits.stream().map(KnowledgeSearchHit::citation).toList();
    }
}
