package com.kangban.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextChunker {

    private static final int MAX_CHARS = 1800;
    private static final int OVERLAP_CHARS = 240;

    public List<KnowledgeChunkDraft> chunk(ParsedDocument document) {
        List<KnowledgeChunkDraft> chunks = new ArrayList<>();
        int index = 0;
        for (ParsedPage page : document.pages()) {
            String text = page.text();
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(text.length(), start + MAX_CHARS);
                if (end < text.length()) {
                    int boundary = lastBoundary(text, start, end);
                    if (boundary > start + MAX_CHARS / 2) {
                        end = boundary;
                    }
                }
                String content = text.substring(start, end).trim();
                if (!content.isBlank()) {
                    chunks.add(new KnowledgeChunkDraft(
                            index++, page.pageNumber(), page.section(), content, tokenCount(content)));
                }
                if (end >= text.length()) {
                    break;
                }
                start = Math.max(start + 1, end - OVERLAP_CHARS);
            }
        }
        return chunks;
    }

    static int tokenCount(String text) {
        return Math.max(1, (int) Math.ceil(text.codePointCount(0, text.length()) / 2.5));
    }

    private int lastBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char current = text.charAt(i);
            if (current == '。' || current == '！' || current == '？' || current == '\n'
                    || current == '.' || current == '!' || current == '?') {
                return i + 1;
            }
        }
        return end;
    }
}
