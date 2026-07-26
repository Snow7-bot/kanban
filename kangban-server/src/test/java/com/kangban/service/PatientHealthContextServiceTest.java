package com.kangban.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangban.common.BusinessException;
import com.kangban.entity.FamilyMember;
import com.kangban.entity.HealthRecord;
import com.kangban.entity.MedicalRecord;
import com.kangban.entity.Medication;
import com.kangban.entity.User;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.HealthRecordMapper;
import com.kangban.mapper.MedicalRecordMapper;
import com.kangban.mapper.MedicationMapper;
import com.kangban.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PatientHealthContextServiceTest {

    private UserMapper userMapper;
    private FamilyMemberMapper familyMemberMapper;
    private HealthRecordMapper healthRecordMapper;
    private MedicalRecordMapper medicalRecordMapper;
    private MedicationMapper medicationMapper;
    private PatientHealthContextService service;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        familyMemberMapper = mock(FamilyMemberMapper.class);
        healthRecordMapper = mock(HealthRecordMapper.class);
        medicalRecordMapper = mock(MedicalRecordMapper.class);
        medicationMapper = mock(MedicationMapper.class);
        service = new PatientHealthContextService(
                userMapper,
                familyMemberMapper,
                healthRecordMapper,
                medicalRecordMapper,
                medicationMapper,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void selfSnapshotUsesDatabaseDataWithoutSensitiveLoginFields() {
        User user = new User();
        user.setId(9L);
        user.setName("李明");
        user.setPhone("13600000000");
        user.setEmail("private@example.com");
        user.setBirthday(LocalDate.of(1986, 7, 20));
        user.setGender("男");
        user.setHeight(178.0);
        user.setWeight(72.0);
        user.setBloodType("A+");

        HealthRecord healthRecord = new HealthRecord();
        healthRecord.setMetric("heart_rate");
        healthRecord.setValue("89");
        healthRecord.setUnit("次/分");
        healthRecord.setRecordedDate(LocalDate.now());

        Medication medication = new Medication();
        medication.setName("阿司匹林");
        medication.setStatus("active");

        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setRecordName("年度体检报告");
        medicalRecord.setRecordDate(LocalDate.now());

        when(userMapper.selectOne(any())).thenReturn(user);
        when(healthRecordMapper.selectList(any())).thenReturn(List.of(healthRecord));
        when(medicationMapper.selectList(any())).thenReturn(List.of(medication));
        when(medicalRecordMapper.selectList(any())).thenReturn(List.of(medicalRecord));

        PatientHealthContextService.Snapshot snapshot = service.build(9L, null);

        assertThat(snapshot.contextJson())
                .contains("\"contextVersion\":\"family-agent-v2\"")
                .contains("\"name\":\"李明\"")
                .contains("\"metric\":\"heart_rate\"")
                .contains("\"name\":\"阿司匹林\"")
                .contains("\"recordName\":\"年度体检报告\"")
                .doesNotContain("13600000000")
                .doesNotContain("private@example.com");
        assertThat(snapshot.initialMessage())
                .contains("李明的独立问诊档案")
                .contains("基本资料：本人")
                .contains("男")
                .contains("178.0cm")
                .contains("72.0kg")
                .contains("A+型血")
                .contains("心率 89 次/分")
                .contains("阿司匹林")
                .contains("年度体检报告");
    }

    @Test
    void familyMemberWithoutHealthDataReportsInsufficientData() {
        FamilyMember member = new FamilyMember();
        member.setId(15L);
        member.setUserId(9L);
        member.setName("王阿姨");
        member.setRelation("母亲");

        when(familyMemberMapper.selectOne(any())).thenReturn(member);
        when(healthRecordMapper.selectList(any())).thenReturn(List.of());
        when(medicationMapper.selectList(any())).thenReturn(List.of());
        when(medicalRecordMapper.selectList(any())).thenReturn(List.of());

        PatientHealthContextService.Snapshot snapshot = service.build(9L, 15L);

        assertThat(snapshot.memberId()).isEqualTo(15L);
        assertThat(snapshot.initialMessage())
                .contains("王阿姨的独立问诊档案")
                .contains("暂无足够健康数据")
                .contains("不会编造结论");
    }

    @Test
    void unauthorizedFamilyMemberIsRejectedBeforeAnyHealthDataQuery() {
        when(familyMemberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.build(9L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");

        verifyNoInteractions(healthRecordMapper, medicationMapper, medicalRecordMapper);
    }
}
