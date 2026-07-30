package com.kangban;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.entity.FamilyGroup;
import com.kangban.entity.FamilyGroupMember;
import com.kangban.entity.FamilyPermission;
import com.kangban.entity.HealthRecord;
import com.kangban.entity.User;
import com.kangban.mapper.*;
import com.kangban.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestCaptchaConfig.class)
class FamilySharingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserMapper userMapper;
    @Autowired private HealthRecordMapper healthRecordMapper;
    @Autowired private FamilyPermissionMapper permissionMapper;
    @Autowired private FamilyInvitationMapper invitationMapper;
    @Autowired private FamilyGroupMemberMapper groupMemberMapper;
    @Autowired private FamilyGroupMapper groupMapper;
    @Autowired private AuditLogMapper auditLogMapper;

    private User viewer;
    private User subject;
    private User outsider;
    private String viewerToken;
    private String subjectToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        auditLogMapper.delete(null);
        permissionMapper.delete(null);
        invitationMapper.delete(null);
        groupMemberMapper.delete(null);
        groupMapper.delete(null);
        healthRecordMapper.delete(null);
        userMapper.delete(null);
        viewer = user("13910000001", "家庭管理员");
        subject = user("13910000002", "被照护人");
        outsider = user("13910000003", "无关用户");
        viewerToken = token(viewer);
        subjectToken = token(subject);
        outsiderToken = token(outsider);
    }

    @Test
    void acceptedInvitationAllowsOnlyExplicitHealthScopes() throws Exception {
        Long invitationId = invite(false);
        accept(invitationId, subjectToken);
        addHealth(subject.getId(), "heart_rate", "76");

        mockMvc.perform(get("/health/trends")
                        .header("Authorization", bearer(viewerToken))
                        .param("subjectUserId", subject.getId().toString())
                        .param("metric", "heart_rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].value").value("76"));

        mockMvc.perform(get("/consultation/sessions")
                        .header("Authorization", bearer(viewerToken))
                        .param("subjectUserId", subject.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void subjectCanEnableAiThenRevokeAllAccessImmediately() throws Exception {
        Long invitationId = invite(false);
        accept(invitationId, subjectToken);
        String permissions = objectMapper.writeValueAsString(Map.of(
                "canViewHealth", true, "canAddHealth", false,
                "canViewRecords", false, "canViewMedications", false,
                "canViewReports", true, "canUseAi", true,
                "canModify", false, "canDelete", false));

        mockMvc.perform(put("/family/sharing/permissions/" + viewer.getId())
                        .header("Authorization", bearer(subjectToken))
                        .contentType(MediaType.APPLICATION_JSON).content(permissions))
                .andExpect(status().isOk());

        mockMvc.perform(get("/consultation/sessions")
                        .header("Authorization", bearer(viewerToken))
                        .param("subjectUserId", subject.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/family/sharing/permissions/" + viewer.getId())
                        .header("Authorization", bearer(subjectToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/health/trends")
                        .header("Authorization", bearer(viewerToken))
                        .param("subjectUserId", subject.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void outsiderCannotAcceptSomeoneElsesInvitation() throws Exception {
        Long invitationId = invite(false);
        mockMvc.perform(post("/family/sharing/invitations/" + invitationId + "/accept")
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void addHealthRequiresSeparateWritePermission() throws Exception {
        Long invitationId = invite(false);
        accept(invitationId, subjectToken);
        String body = objectMapper.writeValueAsString(Map.of(
                "subjectUserId", subject.getId(),
                "metric", "weight", "value", "60", "unit", "kg"));

        mockMvc.perform(post("/health/records")
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void sharedAccountNeverShowsAnotherUserAsSelf() throws Exception {
        FamilyGroup group = new FamilyGroup();
        group.setName("历史家庭");
        group.setOwnerUserId(subject.getId());
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
        groupMapper.insert(group);

        FamilyGroupMember owner = new FamilyGroupMember();
        owner.setFamilyId(group.getId());
        owner.setUserId(subject.getId());
        owner.setRelation("本人");
        owner.setRole("owner");
        owner.setStatus("active");
        owner.setJoinedAt(LocalDateTime.now());
        owner.setCreatedAt(LocalDateTime.now());
        owner.setUpdatedAt(LocalDateTime.now());
        groupMemberMapper.insert(owner);

        FamilyPermission permission = new FamilyPermission();
        permission.setFamilyId(group.getId());
        permission.setSubjectUserId(subject.getId());
        permission.setGranteeUserId(viewer.getId());
        permission.setCanViewHealth(true);
        permission.setCanAddHealth(false);
        permission.setCanViewRecords(false);
        permission.setCanViewMedications(false);
        permission.setCanViewReports(true);
        permission.setCanUseAi(true);
        permission.setCanModify(false);
        permission.setCanDelete(false);
        permission.setStatus("active");
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.insert(permission);

        mockMvc.perform(get("/family/sharing")
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sharedSubjects[0].relation").value("家庭成员"));
    }

    private Long invite(boolean canAddHealth) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", subject.getUsername(),
                "relation", "家人",
                "permissions", Map.of(
                        "canViewHealth", true,
                        "canAddHealth", canAddHealth,
                        "canViewRecords", false,
                        "canViewMedications", false,
                        "canViewReports", true,
                        "canUseAi", false,
                        "canModify", false,
                        "canDelete", false)));
        String response = mockMvc.perform(post("/family/sharing/invitations")
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void accept(Long invitationId, String token) throws Exception {
        mockMvc.perform(post("/family/sharing/invitations/" + invitationId + "/accept")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private User user(String phone, String name) {
        User user = new User();
        user.setUsername("share_" + phone.substring(phone.length() - 4));
        user.setPhone(phone);
        user.setPassword("hash");
        user.setName(name);
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private void addHealth(Long userId, String metric, String value) {
        HealthRecord record = new HealthRecord();
        record.setUserId(userId);
        record.setMetric(metric);
        record.setValue(value);
        record.setUnit("bpm");
        record.setRecordedDate(LocalDate.now());
        record.setCreatedAt(LocalDateTime.now());
        healthRecordMapper.insert(record);
    }

    private String token(User user) {
        return jwtTokenProvider.generateToken(user.getId(), user.getUsername());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
