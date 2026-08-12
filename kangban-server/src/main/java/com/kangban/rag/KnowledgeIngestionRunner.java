package com.kangban.rag;

import org.springframework.scheduling.annotation.Async;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeIngestionRunner {

    private final KnowledgeDocumentService documentService;

    public KnowledgeIngestionRunner(@Lazy KnowledgeDocumentService documentService) {
        this.documentService = documentService;
    }

    @Async("agentTaskExecutor")
    public void run(Long jobId) {
        documentService.processJobNow(jobId);
    }
}
