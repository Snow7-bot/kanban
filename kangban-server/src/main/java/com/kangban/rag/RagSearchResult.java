package com.kangban.rag;

import com.kangban.agent.Citation;
import com.kangban.agent.RagProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public static RagSearchResult merge(RagProperties properties, RagSearchResult... results) {
        List<KnowledgeSearchHit> all = new ArrayList<>();
        for (RagSearchResult result : results) {
            if (result != null) {
                all.addAll(result.hits());
            }
        }
        all.sort(Comparator.comparingDouble(KnowledgeSearchHit::score).reversed());
        List<KnowledgeSearchHit> selected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int usedTokens = 0;
        for (KnowledgeSearchHit hit : all) {
            Citation citation = hit.citation();
            String key = citation.scope() + "|" + citation.documentId() + "|"
                    + hit.content().replaceAll("\\s+", "").toLowerCase();
            if (!seen.add(key)) {
                continue;
            }
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
        return new RagSearchResult(buildContext(selected), selected);
    }

    private static String buildContext(List<KnowledgeSearchHit> hits) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            Citation citation = hits.get(i).citation();
            context.append("[资料").append(i + 1).append("] ").append(citation.title());
            if ("PRIVATE".equals(citation.scope())) {
                context.append("，家庭私有病历");
            }
            if (citation.pageNumber() != null) {
                context.append("，第").append(citation.pageNumber()).append("页");
            }
            if (citation.section() != null && !citation.section().isBlank()) {
                context.append("，章节：").append(citation.section());
            }
            if (citation.source() != null && !citation.source().isBlank()) {
                context.append("，来源：").append(citation.source());
            }
            context.append("\n").append(hits.get(i).content()).append("\n\n");
        }
        return context.toString().trim();
    }
}
