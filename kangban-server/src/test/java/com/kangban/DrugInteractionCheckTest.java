package com.kangban;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.entity.DrugInteractionRule;
import com.kangban.entity.Medication;
import com.kangban.entity.User;
import com.kangban.mapper.DrugInteractionRuleMapper;
import com.kangban.mapper.MedicationMapper;
import com.kangban.mapper.UserMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestVerificationCodeConfig.class)
@DisplayName("P1-B: 药物相互作用检查集成测试")
class DrugInteractionCheckTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MedicationMapper medicationMapper;

    @Autowired
    private DrugInteractionRuleMapper drugInteractionRuleMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private User owner;
    private User otherUser;
    private Medication medWarfarin;
    private Medication medAspirin;
    private Medication medMetformin;
    private Medication medUnknown;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        // Clean up
        drugInteractionRuleMapper.delete(null);
        medicationMapper.delete(null);
        userMapper.delete(null);

        // Users
        owner = createUser("13900000011", "测试用户A");
        otherUser = createUser("13900000012", "测试用户B");

        // Owner's medications with standard_drug_id for rule matching
        medWarfarin = createMedication(owner.getId(), "华法林钠片", "warfarin", "2.5mg", "片");
        medAspirin = createMedication(owner.getId(), "阿司匹林肠溶片", "aspirin", "100mg", "片");
        medMetformin = createMedication(owner.getId(), "盐酸二甲双胍片", "metformin", "500mg", "片");
        medUnknown = createMedication(owner.getId(), "未知草药提取物", null, "10ml", "支");

        // Other user's medication (should be rejected)
        createMedication(otherUser.getId(), "他人的药", "other_drug", "1mg", "片");

        // Seed demo rules matching our standard_drug_ids
        seedRule("warfarin", "aspirin", "high",
                "华法林与阿司匹林合用会显著增加出血风险。",
                "避免联合使用。", "demo-rule", "1.0");

        seedRule("metformin", "alcohol", "medium",
                "二甲双胍与酒精增加乳酸性酸中毒风险。",
                "服药期间避免饮酒。", "demo-rule", "1.0");

        // Tokens
        ownerToken = generateToken(owner.getId().toString());
        otherToken = generateToken(otherUser.getId().toString());
    }

    // ==================== 已知药物组合命中 ====================

    @Nested
    @DisplayName("已知药物组合命中")
    class KnownCombinationHits {

        @Test
        @DisplayName("华法林+阿司匹林 → 高风险相互作用")
        void warfarinAspirinHighRisk() throws Exception {
            String body = objectMapper.writeValueAsString(
                    Map.of("drugIds", List.of(medWarfarin.getId().toString(), medAspirin.getId().toString())));

            String response = mockMvc.perform(post("/medications/interaction")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.hasInteraction").value(true))
                    .andExpect(jsonPath("$.data.overallRiskLevel").value("high"))
                    .andExpect(jsonPath("$.data.matchedRules.length()").value(1))
                    .andExpect(jsonPath("$.data.matchedRules[0].riskLevel").value("high"))
                    .andExpect(jsonPath("$.data.matchedRules[0].drugA").value("华法林钠片"))
                    .andExpect(jsonPath("$.data.matchedRules[0].drugB").value("阿司匹林肠溶片"))
                    .andExpect(jsonPath("$.data.disclaimer").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();

            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertThat((String) data.get("source")).isNotBlank();
        }

        @Test
        @DisplayName("阿司匹林+华法林（顺序互换）同样命中")
        void swappedOrderStillHits() throws Exception {
            String body = objectMapper.writeValueAsString(
                    Map.of("drugIds", List.of(medAspirin.getId().toString(), medWarfarin.getId().toString())));

            mockMvc.perform(post("/medications/interaction")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasInteraction").value(true))
                    .andExpect(jsonPath("$.data.overallRiskLevel").value("high"));
        }
    }

    // ==================== 无规则覆盖 ====================

    @Nested
    @DisplayName("无规则覆盖")
    class NoRuleCoverage {

        @Test
        @DisplayName("无匹配规则时返回空 matchedRules + uncoveredPairs 不为空")
        void noMatchingRuleReturnsUncovered() throws Exception {
            String body = objectMapper.writeValueAsString(
                    Map.of("drugIds", List.of(medMetformin.getId().toString(), medUnknown.getId().toString())));

            mockMvc.perform(post("/medications/interaction")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasInteraction").value(false))
                    .andExpect(jsonPath("$.data.matchedRules.length()").value(0))
                    .andExpect(jsonPath("$.data.uncoveredPairs.length()").value(1))
                    .andExpect(jsonPath("$.data.uncoveredPairs[0].message").value("暂无演示规则覆盖"))
                    .andExpect(jsonPath("$.data.summary").value("所有药物组合均暂无演示规则覆盖，建议咨询医生或药师。"));
        }
    }

    // ==================== 越权检查 ====================

    @Nested
    @DisplayName("越权检查")
    class UnauthorizedAccess {

        @Test
        @DisplayName("非本人药物 ID 被拒绝")
        void otherUsersDrugIdRejected() throws Exception {
            // Get other user's medication
            List<Medication> others = medicationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Medication>()
                            .eq(Medication::getUserId, otherUser.getId()));
            assertThat(others).isNotEmpty();
            Long otherDrugId = others.get(0).getId();

            String body = objectMapper.writeValueAsString(
                    Map.of("drugIds", List.of(medWarfarin.getId().toString(), otherDrugId.toString())));

            mockMvc.perform(post("/medications/interaction")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空输入 → 400")
        void emptyInputRejected() throws Exception {
            String body = objectMapper.writeValueAsString(Map.of("drugIds", List.of()));

            mockMvc.perform(post("/medications/interaction")
                    .header("Authorization", "Bearer " + ownerToken)
                    .contentType("application/json")
                    .content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("重复药物 ID 自动去重")
        void duplicateDrugIdsDeduped() throws Exception {
            String body = objectMapper.writeValueAsString(
                    Map.of("drugIds", List.of(
                            medWarfarin.getId().toString(),
                            medAspirin.getId().toString(),
                            medWarfarin.getId().toString()))); // duplicate

            mockMvc.perform(post("/medications/interaction")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.drugs.length()").value(2))
                    .andExpect(jsonPath("$.data.hasInteraction").value(true));
        }

        @Test
        @DisplayName("单种药物 → 无法检查")
        void singleDrugNoInteraction() throws Exception {
            String body = objectMapper.writeValueAsString(
                    Map.of("drugIds", List.of(medWarfarin.getId().toString())));

            mockMvc.perform(post("/medications/interaction")
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hasInteraction").value(false))
                    .andExpect(jsonPath("$.data.summary").value("需要至少两种药物才能检查相互作用。"));
        }
    }

    // ==================== Helpers ====================

    private User createUser(String phone, String name) {
        User u = new User();
        u.setUsername(phone);
        u.setPhone(phone);
        u.setPassword("$2a$10$dummyhash");
        u.setName(name);
        u.setStatus(1);
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return u;
    }

    private Medication createMedication(Long userId, String name, String standardDrugId, String dosage, String unit) {
        Medication m = new Medication();
        m.setUserId(userId);
        m.setName(name);
        m.setStandardDrugId(standardDrugId);
        m.setDosage(dosage);
        m.setUnit(unit);
        m.setStatus("active");
        m.setCreatedAt(LocalDateTime.now());
        medicationMapper.insert(m);
        return m;
    }

    private void seedRule(String drugA, String drugB, String riskLevel, String desc, String advice, String source, String version) {
        DrugInteractionRule rule = new DrugInteractionRule();
        rule.setDrugA(drugA);
        rule.setDrugB(drugB);
        rule.setRiskLevel(riskLevel);
        rule.setDescription(desc);
        rule.setAdvice(advice);
        rule.setSource(source);
        rule.setVersion(version);
        rule.setActive(1);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        drugInteractionRuleMapper.insert(rule);
    }

    private String generateToken(String userId) {
        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret));
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(key)
                .compact();
    }
}
