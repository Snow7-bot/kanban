package com.kangban.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void chunksLongTextWithStableIndexesAndPageMetadata() {
        String text = "健康提示。".repeat(600);
        TextChunker chunker = new TextChunker();

        List<KnowledgeChunkDraft> chunks = chunker.chunk(
                new ParsedDocument(List.of(new ParsedPage(3, "血压", text))));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).chunkIndex()).isZero();
        assertThat(chunks.get(1).chunkIndex()).isEqualTo(1);
        assertThat(chunks).allMatch(chunk -> chunk.pageNumber() == 3 && chunk.tokenCount() > 0);
    }
}
