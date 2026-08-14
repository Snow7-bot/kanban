package com.kangban.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.agent.AgentExecutionContext;
import com.kangban.agent.RagProperties;
import com.kangban.common.BusinessException;
import com.kangban.service.FamilyAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JdbcPrivateKnowledgeSearchServiceTest {

    @Test
    void returnsOnlyAuthorizedMemberEvidenceAndMarksCitationPrivate() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamilyAccessService accessService = mock(FamilyAccessService.class);
        RagProperties properties = new RagProperties();
        properties.setMinScore(0.7);
        properties.setTopK(3);
        HashEmbeddingClient embeddingClient = new HashEmbeddingClient();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("document_id", 21L);
        row.put("title", "李明血常规");
        row.put("version", 2);
        row.put("source", "家庭私有病历");
        row.put("updated_at", "2026-08-12T10:00");
        row.put("page_number", null);
        row.put("section", "OCR文本");
        row.put("content", "白细胞计数偏高，建议复查。");
        row.put("embedding_json", new ObjectMapper().writeValueAsString(
                embeddingClient.embed("白细胞计数偏高，建议复查。")));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));

        AgentExecutionContext context = context(9L, 15L, 2L);
        RagSearchResult result = new JdbcPrivateKnowledgeSearchService(
                jdbcTemplate, new ObjectMapper(), embeddingClient, properties, accessService)
                .search("白细胞计数", context);

        assertThat(result.hits()).hasSize(1);
        assertThat(result.citations().get(0).scope()).isEqualTo("PRIVATE");
        assertThat(result.citations().get(0).title()).isEqualTo("李明血常规");
        assertThat(result.context()).contains("家庭私有病历");
        verify(accessService).require(9L, 15L, FamilyAccessService.Scope.VIEW_RECORDS);
    }

    @Test
    void deniesPrivateSearchBeforeQueryWhenFamilyPermissionIsRevoked() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamilyAccessService accessService = mock(FamilyAccessService.class);
        doThrow(BusinessException.forbidden("未获得该家庭成员的数据访问权限"))
                .when(accessService).require(9L, 15L, FamilyAccessService.Scope.VIEW_RECORDS);

        RagSearchResult result = new JdbcPrivateKnowledgeSearchService(
                jdbcTemplate, new ObjectMapper(), new HashEmbeddingClient(), new RagProperties(), accessService)
                .search("病历", context(9L, 15L, 2L));

        assertThat(result.hits()).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void ownAccountCanSearchWithoutASeparatePermissionRow() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        FamilyAccessService accessService = mock(FamilyAccessService.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        RagSearchResult result = new JdbcPrivateKnowledgeSearchService(
                jdbcTemplate, new ObjectMapper(), new HashEmbeddingClient(), new RagProperties(), accessService)
                .search("病历", context(15L, 15L, null));

        assertThat(result.hits()).isEmpty();
        verifyNoInteractions(accessService);
        verify(jdbcTemplate).queryForList(anyString(), any(Object[].class));
    }

    private AgentExecutionContext context(Long actor, Long subject, Long member) {
        long now = System.currentTimeMillis() / 1000;
        return new AgentExecutionContext(actor, subject, member, 31L,
                "run-private", "trace-private", now - 1, now + 60);
    }
}
