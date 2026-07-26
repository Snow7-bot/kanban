package com.kangban;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.entity.FamilyMember;
import com.kangban.entity.HealthRecord;
import com.kangban.entity.User;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.HealthRecordMapper;
import com.kangban.mapper.UserMapper;
import com.kangban.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestVerificationCodeConfig.class)
@DisplayName("P2-A: 家庭与健康集成测试")
class FamilyHealthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private FamilyMemberMapper familyMemberMapper;
    @Autowired private HealthRecordMapper healthRecordMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User owner;
    private User otherUser;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        healthRecordMapper.delete(null);
        familyMemberMapper.delete(null);
        userMapper.delete(null);

        owner = createUser("13900000201", "主人");
        otherUser = createUser("13900000202", "他人");
        ownerToken = jwtTokenProvider.generateToken(owner.getId(), owner.getUsername());
        otherToken = jwtTokenProvider.generateToken(otherUser.getId(), otherUser.getUsername());
    }

    private User createUser(String phone, String name) {
        User u = new User();
        u.setUsername("u_" + phone.substring(phone.length() - 4));
        u.setPhone(phone);
        u.setPassword("hash");
        u.setName(name);
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return u;
    }

    private FamilyMember createMember(Long userId, String name, String relation) {
        FamilyMember m = new FamilyMember();
        m.setUserId(userId);
        m.setName(name);
        m.setRelation(relation);
        m.setAge(30);
        m.setGender("女");
        m.setCreatedAt(LocalDateTime.now());
        familyMemberMapper.insert(m);
        return m;
    }

    private void addHealthRecord(Long userId, Long memberId, String memberName, String metric, String value) {
        HealthRecord r = new HealthRecord();
        r.setUserId(userId);
        r.setMemberId(memberId);
        r.setMemberName(memberName);
        r.setMetric(metric);
        r.setValue(value);
        r.setUnit(metric.equals("blood_pressure") ? "mmHg" : "mmol/L");
        r.setRecordedDate(LocalDate.now());
        r.setCreatedAt(LocalDateTime.now());
        healthRecordMapper.insert(r);
    }

    // ==================== 家庭成员归属隔离 ====================

    @Nested
    @DisplayName("家庭成员归属隔离")
    class MemberIsolation {

        @Test
        @DisplayName("主人可以添加家庭成员")
        void ownerCanAddMember() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "name", "李美芳", "relation", "母亲", "age", 55, "gender", "女"));

            mockMvc.perform(post("/family")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.name").value("李美芳"));
        }

        @Test
        @DisplayName("主人可以查看自己的家庭成员列表")
        void ownerCanListMembers() throws Exception {
            createMember(owner.getId(), "李美芳", "母亲");
            createMember(owner.getId(), "张小明", "儿子");

            mockMvc.perform(get("/family")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("其他用户看不到我的家庭成员")
        void otherUserCannotSeeMyMembers() throws Exception {
            FamilyMember member = createMember(owner.getId(), "李美芳", "母亲");

            mockMvc.perform(get("/family/" + member.getId())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("其他用户不能修改我的家庭成员")
        void otherUserCannotUpdateMyMember() throws Exception {
            FamilyMember member = createMember(owner.getId(), "李美芳", "母亲");
            String body = objectMapper.writeValueAsString(Map.of("name", "被篡改"));

            mockMvc.perform(put("/family/" + member.getId())
                            .header("Authorization", "Bearer " + otherToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("其他用户不能删除我的家庭成员")
        void otherUserCannotDeleteMyMember() throws Exception {
            FamilyMember member = createMember(owner.getId(), "李美芳", "母亲");

            mockMvc.perform(delete("/family/" + member.getId())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== 删除存在健康记录的成员 ====================

    @Nested
    @DisplayName("删除存在健康记录的成员")
    class DeleteWithRecords {

        @Test
        @DisplayName("无健康记录的成员可直接删除")
        void deleteMemberWithoutRecords() throws Exception {
            FamilyMember member = createMember(owner.getId(), "李美芳", "母亲");

            mockMvc.perform(delete("/family/" + member.getId())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("有健康记录的成员 → 409 拒绝删除")
        void deleteMemberWithRecordsReturns409() throws Exception {
            FamilyMember member = createMember(owner.getId(), "李美芳", "母亲");
            addHealthRecord(owner.getId(), member.getId(), "李美芳", "blood_pressure", "120/80");

            mockMvc.perform(delete("/family/" + member.getId())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isConflict());
        }
    }

    // ==================== 用户/成员数据隔离 ====================

    @Nested
    @DisplayName("用户/成员数据隔离")
    class DataIsolation {

        @Test
        @DisplayName("本人健康记录不与家庭成员混淆")
        void selfAndMemberDataIsolated() throws Exception {
            FamilyMember member = createMember(owner.getId(), "李美芳", "母亲");
            addHealthRecord(owner.getId(), null, null, "blood_pressure", "118/76");
            addHealthRecord(owner.getId(), member.getId(), "李美芳", "blood_pressure", "125/82");

            // Query self trends (memberId=null means self)
            mockMvc.perform(get("/health/trends")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("metric", "blood_pressure"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(1));

            // Query member trends
            mockMvc.perform(get("/health/trends")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("metric", "blood_pressure")
                            .param("memberId", member.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(1));
        }

        @Test
        @DisplayName("其他用户看不到我的健康记录")
        void otherUserCannotSeeMyHealthRecords() throws Exception {
            addHealthRecord(owner.getId(), null, null, "heart_rate", "72");

            mockMvc.perform(get("/health/trends")
                            .header("Authorization", "Bearer " + otherToken)
                            .param("metric", "heart_rate"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records.length()").value(0));
        }
    }

    // ==================== 趋势与报告 ====================

    @Nested
    @DisplayName("趋势与报告")
    class TrendsAndReports {

        @Test
        @DisplayName("获取健康趋势返回 records + stats")
        void getTrendsReturnsRecordsAndStats() throws Exception {
            addHealthRecord(owner.getId(), null, null, "blood_pressure", "118/76");
            addHealthRecord(owner.getId(), null, null, "blood_pressure", "120/80");

            mockMvc.perform(get("/health/trends")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("metric", "blood_pressure"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.records.length()").value(2))
                    .andExpect(jsonPath("$.data.stats").exists());
        }

        @Test
        @DisplayName("获取健康报告（weekly）")
        void getWeeklyReport() throws Exception {
            addHealthRecord(owner.getId(), null, null, "heart_rate", "72");

            mockMvc.perform(get("/health/report")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("period", "weekly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.period").value("weekly"))
                    .andExpect(jsonPath("$.data.recordCount").exists());
        }

        @Test
        @DisplayName("家庭成员报告与本人报告隔离")
        void memberReportIsolated() throws Exception {
            FamilyMember member = createMember(owner.getId(), "李美芳", "母亲");
            addHealthRecord(owner.getId(), null, null, "heart_rate", "72");
            addHealthRecord(owner.getId(), member.getId(), "李美芳", "heart_rate", "78");

            // Self report
            mockMvc.perform(get("/health/report")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("period", "weekly"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.member").value("本人"));

            // Member report
            mockMvc.perform(get("/health/report")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("period", "weekly")
                            .param("memberId", member.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.member").value("李美芳"));
        }

        @Test
        @DisplayName("获取可用指标类型列表")
        void getMetrics() throws Exception {
            mockMvc.perform(get("/health/metrics"))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== 健康记录 CRUD ====================

    @Nested
    @DisplayName("健康记录 CRUD")
    class HealthRecordCRUD {

        @Test
        @DisplayName("添加健康记录 → 200")
        void addHealthRecordApi() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "metric", "blood_sugar", "value", "5.8", "unit", "mmol/L"));

            mockMvc.perform(post("/health/records")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("删除健康记录 → 200")
        void deleteHealthRecord() throws Exception {
            addHealthRecord(owner.getId(), null, null, "heart_rate", "72");
            HealthRecord rec = healthRecordMapper.selectList(null).get(0);

            mockMvc.perform(delete("/health/records/" + rec.getId())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("其他用户不能删除我的健康记录 → 404")
        void otherUserCannotDeleteMyRecord() throws Exception {
            addHealthRecord(owner.getId(), null, null, "heart_rate", "72");
            HealthRecord rec = healthRecordMapper.selectList(null).get(0);

            mockMvc.perform(delete("/health/records/" + rec.getId())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== 契约 ====================

    @Nested
    @DisplayName("契约验证")
    class ContractVerification {

        @Test
        @DisplayName("家庭成员响应包含 id, name, relation, age, gender")
        void familyMemberResponseShape() throws Exception {
            createMember(owner.getId(), "李美芳", "母亲");
            String response = mockMvc.perform(get("/family")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) result.get("data");
            assertThat(list).isNotEmpty();
            Map<String, Object> member = list.get(0);
            assertThat(member).containsKeys("id", "name", "relation", "age", "gender");
        }

        @Test
        @DisplayName("健康趋势响应包含 records, stats, metric")
        void trendsResponseShape() throws Exception {
            addHealthRecord(owner.getId(), null, null, "blood_pressure", "118/76");
            String response = mockMvc.perform(get("/health/trends")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("metric", "blood_pressure"))
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertThat(data).containsKeys("records", "stats", "metric");
        }

        @Test
        @DisplayName("健康报告响应包含 period, recordCount, member")
        void reportResponseShape() throws Exception {
            addHealthRecord(owner.getId(), null, null, "heart_rate", "72");
            String response = mockMvc.perform(get("/health/report")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("period", "weekly"))
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertThat(data).containsKeys("period", "recordCount", "member", "generatedAt");
        }
    }
}
