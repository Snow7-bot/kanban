package com.kangban.rag;

import com.kangban.agent.AgentExecutionContext;

public interface PrivateKnowledgeSearchService {
    RagSearchResult search(String query, AgentExecutionContext context);
}
