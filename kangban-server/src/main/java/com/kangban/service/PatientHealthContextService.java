package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PatientHealthContextService {

    private static final int MAX_HEALTH_RECORDS = 200;
    private static final int MAX_MEDICAL_RECORDS = 5;
    private static final int MAX_TEXT_LENGTH = 1200;

    private final UserMapper userMapper;
    private final FamilyMemberMapper familyMemberMapper;
    private final HealthRecordMapper healthRecordMapper;
    private final MedicalRecordMapper medicalRecordMapper;
    private final MedicationMapper medicationMapper;
    private final ObjectMapper objectMapper;

    public Snapshot build(Long userId, Long memberId) {
        Map<String, Object> profile = new LinkedHashMap<>();
        String subjectName;

        if (memberId == null) {
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getId, userId)
                            .eq(User::getStatus, 1)
                            .isNull(User::getDeletedAt)
            );
            if (user == null) {
                throw BusinessException.notFound("用户不存在");
            }
            subjectName = firstNonBlank(user.getName(), user.getUsername(), "本人");
            profile.put("name", subjectName);
            profile.put("relation", "本人");
            profile.put("age", calculateAge(user.getBirthday()));
            profile.put("gender", user.getGender());
            profile.put("birthday", user.getBirthday());
            profile.put("bloodType", user.getBloodType());
            profile.put("height", user.getHeight());
            profile.put("weight", user.getWeight());
        } else {
            FamilyMember member = familyMemberMapper.selectOne(
                    new LambdaQueryWrapper<FamilyMember>()
                            .eq(FamilyMember::getId, memberId)
                            .eq(FamilyMember::getUserId, userId)
                            .isNull(FamilyMember::getDeletedAt)
            );
            if (member == null) {
                throw BusinessException.notFound("家庭成员不存在或无权访问");
            }
            subjectName = member.getName();
            profile.put("name", member.getName());
            profile.put("relation", member.getRelation());
            profile.put("age", member.getAge());
            profile.put("gender", member.getGender());
            profile.put("note", member.getNote());
        }

        LocalDate startDate = LocalDate.now().minusDays(29);
        LambdaQueryWrapper<HealthRecord> healthQuery = new LambdaQueryWrapper<HealthRecord>()
                .eq(HealthRecord::getUserId, userId)
                .isNull(HealthRecord::getDeletedAt)
                .ge(HealthRecord::getRecordedDate, startDate)
                .orderByDesc(HealthRecord::getRecordedDate)
                .orderByDesc(HealthRecord::getRecordedTime)
                .last("LIMIT " + MAX_HEALTH_RECORDS);
        if (memberId == null) {
            healthQuery.isNull(HealthRecord::getMemberId);
        } else {
            healthQuery.eq(HealthRecord::getMemberId, memberId);
        }
        List<HealthRecord> healthRecords = healthRecordMapper.selectList(healthQuery);

        LambdaQueryWrapper<Medication> medicationQuery = new LambdaQueryWrapper<Medication>()
                .eq(Medication::getUserId, userId)
                .eq(Medication::getStatus, "active")
                .isNull(Medication::getDeletedAt)
                .orderByDesc(Medication::getCreatedAt);
        if (memberId == null) {
            medicationQuery.isNull(Medication::getMemberId);
        } else {
            medicationQuery.eq(Medication::getMemberId, memberId);
        }
        List<Medication> medications = medicationMapper.selectList(medicationQuery);

        LambdaQueryWrapper<MedicalRecord> medicalRecordQuery = new LambdaQueryWrapper<MedicalRecord>()
                .eq(MedicalRecord::getUserId, userId)
                .isNull(MedicalRecord::getDeletedAt)
                .orderByDesc(MedicalRecord::getRecordDate)
                .orderByDesc(MedicalRecord::getCreatedAt)
                .last("LIMIT " + MAX_MEDICAL_RECORDS);
        if (memberId == null) {
            medicalRecordQuery.isNull(MedicalRecord::getMemberId);
        } else {
            medicalRecordQuery.eq(MedicalRecord::getMemberId, memberId);
        }
        List<MedicalRecord> medicalRecords = medicalRecordMapper.selectList(medicalRecordQuery);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("contextVersion", "family-agent-v2");
        context.put("subject", profile);
        context.put("selectedMemberId", memberId);
        context.put("dataWindow", Map.of(
                "healthMetrics", "最近30天",
                "medicalRecords", "最近5份",
                "medications", "当前有效用药"
        ));
        context.put("healthMetrics", healthRecords.stream().map(this::healthRecordMap).toList());
        context.put("activeMedications", medications.stream().map(this::medicationMap).toList());
        context.put("recentMedicalRecords", medicalRecords.stream().map(this::medicalRecordMap).toList());
        context.put("generatedAt", LocalDateTime.now());
        context.put("medicalNotice", "仅用于健康信息辅助，不替代医生诊断或处方。");

        try {
            String contextJson = objectMapper.writeValueAsString(context);
            String initialMessage = buildInitialMessage(
                    subjectName, profile, healthRecords, medications, medicalRecords);
            return new Snapshot(memberId, subjectName, profile, contextJson, initialMessage);
        } catch (JsonProcessingException e) {
            throw new BusinessException("患者健康上下文生成失败");
        }
    }

    private Map<String, Object> healthRecordMap(HealthRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("metric", record.getMetric());
        map.put("value", record.getValue());
        map.put("unit", record.getUnit());
        map.put("recordedDate", record.getRecordedDate());
        map.put("recordedTime", record.getRecordedTime());
        map.put("note", truncate(record.getNote()));
        return map;
    }

    private Map<String, Object> medicationMap(Medication medication) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", medication.getName());
        map.put("standardName", medication.getStandardDrugName());
        map.put("dosage", medication.getDosage());
        map.put("unit", medication.getUnit());
        map.put("frequency", medication.getFrequency());
        map.put("instruction", medication.getInstruction());
        map.put("times", medication.getTimes());
        map.put("startDate", medication.getStartDate());
        map.put("endDate", medication.getEndDate());
        return map;
    }

    private Map<String, Object> medicalRecordMap(MedicalRecord record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recordName", record.getRecordName());
        map.put("recordType", record.getRecordType());
        map.put("recordDate", record.getRecordDate());
        map.put("hospital", record.getHospital());
        map.put("department", record.getDepartment());
        map.put("diagnosisData", truncate(record.getDiagnosisData()));
        map.put("medicationsData", truncate(record.getMedicationsData()));
        map.put("advicesData", truncate(record.getAdvicesData()));
        map.put("ocrExcerpt", truncate(record.getOcrText()));
        return map;
    }

    private String buildInitialMessage(String subjectName,
                                       Map<String, Object> profile,
                                       List<HealthRecord> healthRecords,
                                       List<Medication> medications,
                                       List<MedicalRecord> medicalRecords) {
        List<String> sources = new ArrayList<>();
        sources.add("最近30天健康指标 " + healthRecords.size() + " 条");
        sources.add("最近病历 " + medicalRecords.size() + " 份");
        sources.add("当前有效用药 " + medications.size() + " 项");

        StringBuilder message = new StringBuilder();
        message.append("已切换到").append(subjectName).append("的独立问诊档案。我已从数据库读取：")
                .append(String.join("、", sources)).append("。");

        List<String> basics = new ArrayList<>();
        addBasic(basics, profile.get("relation"), null);
        addBasic(basics, profile.get("age"), "岁");
        addBasic(basics, profile.get("gender"), null);
        addBasic(basics, profile.get("height"), "cm");
        addBasic(basics, profile.get("weight"), "kg");
        addBasic(basics, profile.get("bloodType"), "型血");
        if (!basics.isEmpty()) {
            message.append("\n\n基本资料：").append(String.join("，", basics)).append("。");
        }

        if (healthRecords.isEmpty() && medications.isEmpty() && medicalRecords.isEmpty()) {
            message.append("\n\n初步分析：目前暂无足够健康数据，无法进行可靠的个性化趋势判断。我不会编造结论，您可以先补充健康指标、病历或用药记录，也可以直接描述当前症状。");
        } else {
            message.append("\n\n初步分析：");
            if (!healthRecords.isEmpty()) {
                HealthRecord latest = healthRecords.get(0);
                message.append("最近一条指标为")
                        .append(displayMetric(latest.getMetric()))
                        .append(" ")
                        .append(latest.getValue())
                        .append(latest.getUnit() == null ? "" : " " + latest.getUnit())
                        .append("；");
            }
            if (!medications.isEmpty()) {
                message.append("当前记录用药包括")
                        .append(String.join("、", medications.stream().map(Medication::getName).limit(5).toList()))
                        .append("；");
            }
            if (!medicalRecords.isEmpty()) {
                message.append("最近病历为")
                        .append(firstNonBlank(medicalRecords.get(0).getRecordName(), "未命名病历"))
                        .append("。");
            }
            message.append("这些信息适合作为后续问诊背景，但不能单独构成诊断。请告诉我现在最需要关注的问题。");
        }
        return message.toString();
    }

    private void addBasic(List<String> basics, Object value, String suffix) {
        if (value == null || String.valueOf(value).isBlank()) return;
        basics.add(String.valueOf(value) + (suffix == null ? "" : suffix));
    }

    private String displayMetric(String metric) {
        if (metric == null) return "健康指标";
        return switch (metric) {
            case "heart_rate" -> "心率";
            case "blood_pressure" -> "血压";
            case "blood_glucose" -> "血糖";
            case "weight" -> "体重";
            case "steps" -> "步数";
            default -> metric;
        };
    }

    private Integer calculateAge(LocalDate birthday) {
        return birthday == null ? null : Period.between(birthday, LocalDate.now()).getYears();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) return value;
        return value.substring(0, MAX_TEXT_LENGTH) + "…";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    public record Snapshot(Long memberId,
                           String subjectName,
                           Map<String, Object> profile,
                           String contextJson,
                           String initialMessage) {
    }
}
