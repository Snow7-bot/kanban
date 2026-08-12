package com.kangban.rag;

public interface DocumentParser {
    ParsedDocument parse(String fileName, String mediaType, byte[] bytes);
}
