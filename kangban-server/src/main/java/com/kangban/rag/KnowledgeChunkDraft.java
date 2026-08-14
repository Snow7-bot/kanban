package com.kangban.rag;

public record KnowledgeChunkDraft(
        int chunkIndex,
        Integer pageNumber,
        String section,
        String content,
        int tokenCount
) {
}
