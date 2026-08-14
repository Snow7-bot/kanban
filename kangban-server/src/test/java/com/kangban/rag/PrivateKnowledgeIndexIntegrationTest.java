package com.kangban.rag;

import com.kangban.TestCaptchaConfig;
import com.kangban.entity.MedicalRecord;
import com.kangban.entity.FamilyGroup;
import com.kangban.entity.FamilyGroupMember;
import com.kangban.entity.FamilyPermission;
import com.kangban.mapper.MedicalRecordMapper;
import com.kangban.mapper.FamilyGroupMapper;
import com.kangban.mapper.FamilyGroupMemberMapper;
import com.kangban.mapper.FamilyPermissionMapper;
import com.kangban.service.MedicalRecordService;
import com.kangban.agent.AgentExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestCaptchaConfig.class)
class PrivateKnowledgeIndexIntegrationTest {

    @Autowired private PrivateKnowledgeIndexService indexService;
    @Autowired private MedicalRecordMapper medicalRecordMapper;
    @Autowired private FamilyGroupMapper familyGroupMapper;
    @Autowired private FamilyGroupMemberMapper familyGroupMemberMapper;
    @Autowired private FamilyPermissionMapper familyPermissionMapper;
    @Autowired private JdbcPrivateKnowledgeSearchService searchService;
    @Autowired private MedicalRecordService medicalRecordService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanPrivateIndex() {
        jdbcTemplate.update("DELETE FROM knowledge_outbox_events");
        jdbcTemplate.update("DELETE FROM family_knowledge_chunks");
        jdbcTemplate.update("DELETE FROM family_knowledge_documents");
        jdbcTemplate.update("DELETE FROM medical_records");
        jdbcTemplate.update("DELETE FROM family_permissions");
        jdbcTemplate.update("DELETE FROM family_group_members");
        jdbcTemplate.update("DELETE FROM family_groups");
    }

    @Test
    void indexesCompletedOcrRecordIdempotentlyAndRevokesIt() {
        MedicalRecord record = new MedicalRecord();
        record.setUserId(901L);
        record.setMemberId(7L);
        record.setRecordName("血常规报告");
        record.setRecordType("PDF");
        record.setStatus("completed");
        record.setOcrText("白细胞计数偏高，建议一周后复查。\n血小板计数正常。");
        record.setDiagnosisData("{\"诊断\":\"待复查\"}");
        medicalRecordMapper.insert(record);

        indexService.indexCompletedRecord(record.getId());
        indexService.indexCompletedRecord(record.getId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_knowledge_documents WHERE medical_record_id=?", Integer.class,
                record.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_knowledge_chunks WHERE document_id=(SELECT id FROM family_knowledge_documents WHERE medical_record_id=?)",
                Integer.class, record.getId())).isGreaterThan(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM knowledge_outbox_events WHERE medical_record_id=?", Integer.class,
                record.getId())).isEqualTo(1);

        indexService.revokeRecord(record.getId(), record.getUserId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM family_knowledge_documents WHERE medical_record_id=?", String.class,
                record.getId())).isEqualTo("REVOKED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_knowledge_documents WHERE medical_record_id=? AND deleted_at IS NOT NULL",
                Integer.class, record.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_knowledge_chunks WHERE document_id=(SELECT id FROM family_knowledge_documents WHERE medical_record_id=?)",
                Integer.class, record.getId())).isZero();
    }

    @Test
    void batchReindexOnlyIndexesCompletedRecordsOwnedByRequestedScope() {
        MedicalRecord completed = record(902L, null, "已完成病历", "血压 120/80");
        MedicalRecord pending = record(902L, null, "处理中病历", "");
        MedicalRecord anotherOwner = record(903L, null, "其他账号病历", "不可跨账号索引");

        Map<String, Object> result = medicalRecordService.reindexPrivateBatch(902L, null, 100);

        assertThat(result).containsEntry("scanned", 2)
                .containsEntry("indexed", 1)
                .containsEntry("skipped", 1)
                .containsEntry("failed", 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_knowledge_documents WHERE medical_record_id=?", Integer.class,
                completed.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_knowledge_documents WHERE medical_record_id=?", Integer.class,
                anotherOwner.getId())).isZero();

        completed.setStatus("pending");
        medicalRecordMapper.updateById(completed);
        Map<String, Object> cleanup = medicalRecordService.reindexPrivateBatch(902L, null, 100);
        assertThat(cleanup).containsEntry("scanned", 2)
                .containsEntry("indexed", 0)
                .containsEntry("skipped", 2)
                .containsEntry("failed", 0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM family_knowledge_chunks WHERE document_id=(SELECT id FROM family_knowledge_documents WHERE medical_record_id=?)",
                Integer.class, completed.getId())).isZero();
    }

    @Test
    void familyPermissionRevokeStopsPrivateSearchImmediately() {
        FamilyGroup family = new FamilyGroup();
        family.setName("测试家庭");
        family.setOwnerUserId(904L);
        familyGroupMapper.insert(family);
        member(family.getId(), 904L, "owner");
        member(family.getId(), 905L, "member");

        FamilyPermission permission = new FamilyPermission();
        permission.setFamilyId(family.getId());
        permission.setSubjectUserId(904L);
        permission.setGranteeUserId(905L);
        permission.setCanViewRecords(true);
        permission.setStatus("active");
        familyPermissionMapper.insert(permission);

        MedicalRecord record = record(904L, null, "家庭病历", "家庭成员血糖偏高");
        indexService.indexCompletedRecord(record.getId());

        AgentExecutionContext context = context(905L, 904L, null);
        assertThat(searchService.search("血糖", context).hits()).isNotEmpty();

        permission.setStatus("revoked");
        permission.setRevokedAt(java.time.LocalDateTime.now());
        familyPermissionMapper.updateById(permission);

        assertThat(searchService.search("血糖", context).hits()).isEmpty();
    }

    private MedicalRecord record(Long userId, Long memberId, String name, String text) {
        MedicalRecord record = new MedicalRecord();
        record.setUserId(userId);
        record.setMemberId(memberId);
        record.setRecordName(name);
        record.setRecordType("PDF");
        record.setStatus(text.isBlank() ? "pending" : "completed");
        record.setOcrText(text);
        medicalRecordMapper.insert(record);
        return record;
    }

    private void member(Long familyId, Long userId, String role) {
        FamilyGroupMember member = new FamilyGroupMember();
        member.setFamilyId(familyId);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("active");
        familyGroupMemberMapper.insert(member);
    }

    private AgentExecutionContext context(Long actor, Long subject, Long memberId) {
        long now = System.currentTimeMillis() / 1000;
        return new AgentExecutionContext(actor, subject, memberId, 41L,
                "run-family", "trace-family", now - 1, now + 60);
    }
}
