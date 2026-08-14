package com.kangban.agent;

/**
 * RAG 引用的稳定数据结构。第一阶段先建立跨同步/SSE 共用的返回类型。
 */
public record Citation(
        String documentId,
        String title,
        String version,
        Integer pageNumber,
        String section,
        String source,
        String updatedAt,
        String scope
) {

    public Citation(String documentId, String title, String version, Integer pageNumber,
                    String section, String source, String updatedAt) {
        this(documentId, title, version, pageNumber, section, source, updatedAt, "PUBLIC");
    }
}
