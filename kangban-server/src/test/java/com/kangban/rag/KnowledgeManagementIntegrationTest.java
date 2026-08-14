package com.kangban.rag;

import com.kangban.TestCaptchaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestCaptchaConfig.class)
class KnowledgeManagementIntegrationTest {

    @Autowired private KnowledgeDocumentService documentService;
    @Autowired private KnowledgeSearchService searchService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KnowledgeIngestionRunner ingestionRunner;

    @BeforeEach
    void cleanKnowledgeTables() {
        jdbcTemplate.update("DELETE FROM ingestion_jobs");
        jdbcTemplate.update("DELETE FROM knowledge_chunks");
        jdbcTemplate.update("DELETE FROM knowledge_documents");
    }

    @Test
    void uploadProcessReviewPublishSearchAndRevokeLifecycle() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "blood-pressure.md", "text/markdown",
                "# 血压管理\n\n血压管理应每日固定时间记录，并在持续异常时及时就医。"
                        .getBytes(StandardCharsets.UTF_8));

        Map<String, Object> created = documentService.upload(9L, "血压管理指南", "官方资料", null, file);
        Long documentId = ((Number) created.get("documentId")).longValue();
        Long jobId = ((Number) created.get("jobId")).longValue();
        verify(ingestionRunner).run(jobId);

        documentService.processJobNow(jobId);
        assertThat(documentService.getJob(jobId).get("status")).isEqualTo("SUCCEEDED");
        assertThat(documentService.chunks(documentId)).isNotEmpty();

        documentService.submitReview(9L, documentId);
        documentService.publish(9L, documentId, "首批官方资料");
        RagSearchResult published = searchService.search("血压管理");
        assertThat(published.hits()).isNotEmpty();
        assertThat(published.citations().get(0).title()).isEqualTo("血压管理指南");

        documentService.revoke(9L, documentId, "资料更新");
        assertThat(searchService.search("血压管理").hits()).isEmpty();
    }
}
