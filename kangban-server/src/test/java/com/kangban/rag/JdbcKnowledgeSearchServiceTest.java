package com.kangban.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcKnowledgeSearchServiceTest {

    @Test
    void searchesOnlyPublishedRowsAndBuildsStructuredCitations() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        HashEmbeddingClient embeddingClient = new HashEmbeddingClient();
        RagProperties properties = new RagProperties();
        properties.setMinScore(0.7);
        properties.setTopK(3);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("document_id", 11L);
        row.put("title", "家庭血压管理");
        row.put("version", 1);
        row.put("source", "官方健康指南");
        row.put("updated_at", "2026-08-12T10:00");
        row.put("page_number", 2);
        row.put("section", "血压");
        row.put("content", "血压记录建议每天固定时间进行。");
        row.put("embedding_json", new ObjectMapper().writeValueAsString(
                embeddingClient.embed("血压记录建议每天固定时间进行。")));
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(row));

        RagSearchResult result = new JdbcKnowledgeSearchService(
                jdbcTemplate, new ObjectMapper(), embeddingClient, properties).search("血压记录");

        assertThat(result.hits()).hasSize(1);
        assertThat(result.citations().get(0).documentId()).isEqualTo("11");
        assertThat(result.citations().get(0).pageNumber()).isEqualTo(2);
        assertThat(result.context()).contains("[资料1]").contains("官方健康指南");
    }
}
