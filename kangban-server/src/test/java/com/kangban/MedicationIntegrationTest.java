package com.kangban;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.entity.Medication;
import com.kangban.entity.User;
import com.kangban.mapper.DoseRecordMapper;
import com.kangban.mapper.MedicationMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestVerificationCodeConfig.class)
@DisplayName("P2-A: 用药管理集成测试")
class MedicationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private MedicationMapper medicationMapper;
    @Autowired private DoseRecordMapper doseRecordMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User owner;
    private User otherUser;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        doseRecordMapper.delete(null);
        medicationMapper.delete(null);
        userMapper.delete(null);

        owner = createUser("13900000301", "用药主人");
        otherUser = createUser("13900000302", "他人");
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

    private Medication createMedication(Long userId, String name, String dosage, int inventory) {
        Medication m = new Medication();
        m.setUserId(userId);
        m.setName(name);
        m.setDosage(dosage);
        m.setUnit("mg");
        m.setInventory(inventory);
        m.setFrequency("每日一次");
        m.setTimes("[\"08:00\"]");
        m.setStatus("active");
        m.setCreatedAt(LocalDateTime.now());
        medicationMapper.insert(m);
        return m;
    }

    // ==================== CRUD ====================

    @Nested
    @DisplayName("CRUD")
    class CrudTests {

        @Test
        @DisplayName("添加用药 → 200")
        void addMedication() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of(
                    "name", "氨氯地平片", "dosage", "5", "unit", "mg",
                    "frequency", "每日一次", "inventory", 30));

            mockMvc.perform(post("/medications")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.name").value("氨氯地平片"));
        }

        @Test
        @DisplayName("获取用药列表 → 200")
        void listMedications() throws Exception {
            createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            createMedication(owner.getId(), "二甲双胍", "500mg", 60);

            mockMvc.perform(get("/medications")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.list.length()").value(2));
        }

        @Test
        @DisplayName("获取用药详情 → 200")
        void getMedicationById() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(get("/medications/" + med.getId())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("氨氯地平片"))
                    .andExpect(jsonPath("$.data.inventory").value(30));
        }

        @Test
        @DisplayName("更新用药 → 200")
        void updateMedication() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            String body = objectMapper.writeValueAsString(Map.of(
                    "name", "氨氯地平片（更新）", "dosage", "10"));

            mockMvc.perform(put("/medications/" + med.getId())
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            // Verify update
            Medication updated = medicationMapper.selectById(med.getId());
            assertThat(updated.getName()).isEqualTo("氨氯地平片（更新）");
        }

        @Test
        @DisplayName("删除用药 → 200（软删除）")
        void deleteMedication() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(delete("/medications/" + med.getId())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk());

            // Verify soft-deleted
            Medication deleted = medicationMapper.selectById(med.getId());
            assertThat(deleted.getDeletedAt()).isNotNull();
        }
    }

    // ==================== 确认服药 ====================

    @Nested
    @DisplayName("确认服药")
    class ConfirmDoseTests {

        @Test
        @DisplayName("确认服药 → 200")
        void confirmDose() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(post("/medications/" + med.getId() + "/confirm")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("今日重复确认服药 → 幂等（不重复扣库存）")
        void confirmDoseIdempotent() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(post("/medications/" + med.getId() + "/confirm")
                    .header("Authorization", "Bearer " + ownerToken));
            mockMvc.perform(post("/medications/" + med.getId() + "/confirm")
                    .header("Authorization", "Bearer " + ownerToken));

            Medication after = medicationMapper.selectById(med.getId());
            assertThat(after.getInventory()).isEqualTo(29); // Only deducted once
        }

        @Test
        @DisplayName("确认服药后库存减少 1")
        void inventoryDeductedAfterConfirm() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(post("/medications/" + med.getId() + "/confirm")
                    .header("Authorization", "Bearer " + ownerToken));

            Medication after = medicationMapper.selectById(med.getId());
            assertThat(after.getInventory()).isEqualTo(29);
        }
    }

    // ==================== 服药历史 ====================

    @Nested
    @DisplayName("服药历史")
    class HistoryTests {

        @Test
        @DisplayName("获取服药历史 → 200")
        void getHistory() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            mockMvc.perform(post("/medications/" + med.getId() + "/confirm")
                    .header("Authorization", "Bearer " + ownerToken));

            mockMvc.perform(get("/medications/history")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("medicationId", med.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("无服药记录时返回空列表")
        void historyEmptyWhenNoRecords() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(get("/medications/history")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("medicationId", med.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    // ==================== 搜索药品 ====================

    @Nested
    @DisplayName("搜索药品")
    class SearchTests {

        @Test
        @DisplayName("按名称搜索 → 200")
        void searchByName() throws Exception {
            createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            createMedication(owner.getId(), "二甲双胍", "500mg", 60);

            mockMvc.perform(get("/medications/search")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("keyword", "氨氯"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("搜索结果不包含其他用户的药物")
        void searchExcludesOthers() throws Exception {
            createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            createMedication(otherUser.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(get("/medications/search")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("keyword", "氨氯"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1));
        }
    }

    // ==================== 越权访问 ====================

    @Nested
    @DisplayName("越权访问")
    class UnauthorizedAccess {

        @Test
        @DisplayName("其他用户看不到我的用药 → 404")
        void otherUserCannotSeeMyMedication() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(get("/medications/" + med.getId())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("其他用户不能确认我的服药 → 404")
        void otherUserCannotConfirmMyDose() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(post("/medications/" + med.getId() + "/confirm")
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("其他用户不能删除我的用药 → 404")
        void otherUserCannotDeleteMyMedication() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);

            mockMvc.perform(delete("/medications/" + med.getId())
                            .header("Authorization", "Bearer " + otherToken))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("未登录 → 403")
        void unauthenticatedAccessDenied() throws Exception {
            mockMvc.perform(get("/medications"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("搜索药物不返回其他用户的药物")
        void searchDoesNotLeakOthers() throws Exception {
            createMedication(owner.getId(), "私密药品A", "10mg", 10);
            createMedication(otherUser.getId(), "他人药品", "20mg", 20);

            mockMvc.perform(get("/medications/search")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("keyword", "药品"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }
    }

    // ==================== 契约验证 ====================

    @Nested
    @DisplayName("契约验证")
    class ContractVerification {

        @Test
        @DisplayName("用药列表响应包含标准字段")
        void medicationListShape() throws Exception {
            createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            String response = mockMvc.perform(get("/medications")
                            .header("Authorization", "Bearer " + ownerToken))
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            assertThat(result).containsKeys("list", "total", "page", "pageSize");
        }

        @Test
        @DisplayName("用药详情响应包含 id, name, dosage, unit, inventory")
        void medicationDetailShape() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            String response = mockMvc.perform(get("/medications/" + med.getId())
                            .header("Authorization", "Bearer " + ownerToken))
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertThat(data).containsKeys("id", "name", "dosage", "unit", "inventory", "frequency");
        }

        @Test
        @DisplayName("服药历史响应包含 id, medicationId, status, confirmedAt")
        void historyShape() throws Exception {
            Medication med = createMedication(owner.getId(), "氨氯地平片", "5mg", 30);
            mockMvc.perform(post("/medications/" + med.getId() + "/confirm")
                    .header("Authorization", "Bearer " + ownerToken));
            String response = mockMvc.perform(get("/medications/history")
                            .header("Authorization", "Bearer " + ownerToken)
                            .param("medicationId", med.getId().toString()))
                    .andReturn().getResponse().getContentAsString();
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            java.util.List<Map<String, Object>> data = (java.util.List<Map<String, Object>>) result.get("data");
            assertThat(data).isNotEmpty();
            assertThat(data.get(0)).containsKeys("id", "medicationId", "status", "confirmedAt");
        }
    }
}
