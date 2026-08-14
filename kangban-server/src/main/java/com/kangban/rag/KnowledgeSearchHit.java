package com.kangban.rag;

import com.kangban.agent.Citation;

public record KnowledgeSearchHit(String content, double score, Citation citation) {
}
