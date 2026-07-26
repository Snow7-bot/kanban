package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kangban.common.BusinessException;
import com.kangban.common.PageResult;
import com.kangban.common.Result;
import com.kangban.dto.request.AddMedicationRequest;
import com.kangban.dto.request.UpdateMedicationRequest;
import com.kangban.entity.DoseRecord;
import com.kangban.entity.DrugInteractionResult;
import com.kangban.entity.DrugInteractionRule;
import com.kangban.entity.FamilyMember;
import com.kangban.entity.Medication;
import com.kangban.mapper.DoseRecordMapper;
import com.kangban.mapper.DrugInteractionResultMapper;
import com.kangban.mapper.DrugInteractionRuleMapper;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.MedicationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MedicationService {

    private final MedicationMapper medicationMapper;
    private final DoseRecordMapper doseRecordMapper;
    private final DrugInteractionResultMapper drugInteractionResultMapper;
    private final DrugInteractionRuleMapper drugInteractionRuleMapper;
    private final FamilyMemberMapper familyMemberMapper;

    public PageResult<Map<String, Object>> list(Long userId, Integer page, Integer pageSize,
                                                String status, Long memberId) {
        validateMemberAccess(userId, memberId);
        Page<Medication> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Medication> wrapper = new LambdaQueryWrapper<Medication>()
                .eq(Medication::getUserId, userId)
                .isNull(Medication::getDeletedAt);

        if (memberId == null) {
            wrapper.isNull(Medication::getMemberId);
        } else {
            wrapper.eq(Medication::getMemberId, memberId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Medication::getStatus, status);
        }
        wrapper.orderByDesc(Medication::getCreatedAt);

        Page<Medication> result = medicationMapper.selectPage(pageParam, wrapper);

        Set<Long> confirmedToday = findConfirmedToday(userId, result.getRecords());
        List<Map<String, Object>> list = result.getRecords().stream()
                .map(medication -> toMap(medication, confirmedToday.contains(medication.getId())))
                .toList();
        return PageResult.of(list, result.getTotal(), page, pageSize);
    }

    public Result<Map<String, Object>> getById(Long userId, Long id) {
        Medication medication = medicationMapper.selectOne(
                new LambdaQueryWrapper<Medication>()
                        .eq(Medication::getId, id)
                        .eq(Medication::getUserId, userId)
                        .isNull(Medication::getDeletedAt));
        if (medication == null) {
            throw BusinessException.notFound("用药提醒不存在");
        }
        return Result.success(toMap(medication));
    }

    public Result<Map<String, Object>> add(Long userId, AddMedicationRequest request) {
        validateMemberAccess(userId, request.getMemberId());
        Medication medication = new Medication();
        medication.setUserId(userId);
        medication.setMemberId(request.getMemberId());
        medication.setName(request.getName());
        medication.setDosage(request.getDosage());
        medication.setUnit(request.getUnit());
        medication.setInstruction(request.getInstruction());
        medication.setFrequency(request.getFrequency());
        medication.setInventory(request.getInventory());
        medication.setTimes(serializeTimes(request.getTimes()));
        medication.setStatus("active");
        medication.setCreatedAt(LocalDateTime.now());
        medicationMapper.insert(medication);
        return Result.success("添加成功", toMap(medication));
    }

    public void update(Long userId, Long id, UpdateMedicationRequest request) {
        Medication medication = medicationMapper.selectOne(
                new LambdaQueryWrapper<Medication>()
                        .eq(Medication::getId, id)
                        .eq(Medication::getUserId, userId)
                        .isNull(Medication::getDeletedAt));
        if (medication == null) {
            throw BusinessException.notFound("用药提醒不存在");
        }
        if (request.getName() != null) {
            medication.setName(request.getName());
        }
        if (request.getDosage() != null) {
            medication.setDosage(request.getDosage());
        }
        if (request.getUnit() != null) {
            medication.setUnit(request.getUnit());
        }
        if (request.getInstruction() != null) {
            medication.setInstruction(request.getInstruction());
        }
        if (request.getFrequency() != null) {
            medication.setFrequency(request.getFrequency());
        }
        if (request.getInventory() != null) {
            medication.setInventory(request.getInventory());
        }
        if (request.getTimes() != null) {
            medication.setTimes(serializeTimes(request.getTimes()));
        }
        if (request.getStatus() != null) {
            medication.setStatus(request.getStatus());
        }
        medication.setUpdatedAt(LocalDateTime.now());
        medicationMapper.updateById(medication);
    }

    public void delete(Long userId, Long id) {
        Medication medication = medicationMapper.selectOne(
                new LambdaQueryWrapper<Medication>()
                        .eq(Medication::getId, id)
                        .eq(Medication::getUserId, userId)
                        .isNull(Medication::getDeletedAt));
        if (medication == null) {
            throw BusinessException.notFound("用药提醒不存在");
        }
        medication.setDeletedAt(LocalDateTime.now());
        medicationMapper.updateById(medication);
    }

    @Transactional
    public void confirmDose(Long userId, Long medicationId) {
        Medication medication = medicationMapper.selectOne(
                new LambdaQueryWrapper<Medication>()
                        .eq(Medication::getId, medicationId)
                        .eq(Medication::getUserId, userId)
                        .isNull(Medication::getDeletedAt));
        if (medication == null) {
            throw BusinessException.notFound("用药提醒不存在");
        }

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime nextDay = startOfDay.plusDays(1);
        Long alreadyConfirmed = doseRecordMapper.selectCount(new LambdaQueryWrapper<DoseRecord>()
                .eq(DoseRecord::getMedicationId, medicationId)
                .eq(DoseRecord::getUserId, userId)
                .in(DoseRecord::getStatus, List.of("confirmed", "completed"))
                .ge(DoseRecord::getConfirmedAt, startOfDay)
                .lt(DoseRecord::getConfirmedAt, nextDay));
        if (alreadyConfirmed != null && alreadyConfirmed > 0) {
            return;
        }

        DoseRecord doseRecord = new DoseRecord();
        doseRecord.setMedicationId(medicationId);
        doseRecord.setUserId(userId);
        doseRecord.setScheduledTime(LocalTime.now());
        doseRecord.setConfirmedAt(LocalDateTime.now());
        doseRecord.setStatus("completed");
        doseRecord.setCreatedAt(LocalDateTime.now());
        doseRecordMapper.insert(doseRecord);

        if (medication.getInventory() != null && medication.getInventory() > 0) {
            medication.setInventory(medication.getInventory() - 1);
            medication.setUpdatedAt(LocalDateTime.now());
            medicationMapper.updateById(medication);
        }
    }

    /**
     * Check drug interactions using demo rule engine.
     * Matches medications by standard_drug_id or normalized drug name.
     */
    @Transactional
    public Map<String, Object> checkInteraction(Long userId, List<String> drugIdStrings) {
        // --- Validate input ---
        if (drugIdStrings == null || drugIdStrings.isEmpty()) {
            throw BusinessException.paramsError("请至少选择一种药物进行检查");
        }

        // --- Deduplicate and parse ---
        List<Long> dedupedIds = drugIdStrings.stream()
                .distinct()
                .map(id -> {
                    try { return Long.parseLong(id); }
                    catch (NumberFormatException e) { return null; }
                })
                .filter(Objects::nonNull)
                .toList();

        if (dedupedIds.isEmpty()) {
            throw BusinessException.paramsError("无效的药物ID");
        }

        // --- Fetch medications, verify ownership ---
        List<Medication> medications = medicationMapper.selectBatchIds(dedupedIds);
        if (medications.size() != dedupedIds.size()) {
            Set<Long> found = medications.stream().map(Medication::getId).collect(Collectors.toSet());
            List<Long> missing = dedupedIds.stream().filter(id -> !found.contains(id)).toList();
            throw BusinessException.paramsError("以下药物不存在: " + missing);
        }

        for (Medication med : medications) {
            if (!med.getUserId().equals(userId)) {
                throw BusinessException.forbidden("无权检查他人药物: " + med.getId());
            }
        }

        // --- Single drug: no interaction possible ---
        if (medications.size() < 2) {
            Map<String, Object> result = buildEmptyResult(medications);
            return result;
        }

        // --- Build all unique pairs ---
        List<DrugPair> pairs = buildPairs(medications);

        // --- Resolve standard identifiers for matching ---
        Map<Long, String> drugIdToStandard = new LinkedHashMap<>();
        Map<Long, String> drugIdToDisplayName = new LinkedHashMap<>();
        for (Medication med : medications) {
            String standard = resolveStandardIdentifier(med);
            drugIdToStandard.put(med.getId(), standard);
            drugIdToDisplayName.put(med.getId(), med.getName());
        }

        // --- Look up rules for each pair ---
        List<Map<String, Object>> matchedRules = new ArrayList<>();
        List<Map<String, Object>> uncoveredPairs = new ArrayList<>();
        Set<Long> matchedRuleIds = new LinkedHashSet<>();

        for (DrugPair pair : pairs) {
            String idA = drugIdToStandard.get(pair.drugAId);
            String idB = drugIdToStandard.get(pair.drugBId);
            String nameA = drugIdToDisplayName.get(pair.drugAId);
            String nameB = drugIdToDisplayName.get(pair.drugBId);

            // Query rule in both directions
            DrugInteractionRule rule = findRule(idA, idB);

            if (rule != null) {
                Map<String, Object> ruleMap = ruleToMap(rule, nameA, nameB);
                matchedRules.add(ruleMap);
                matchedRuleIds.add(rule.getId());
            } else {
                Map<String, Object> uncovered = new LinkedHashMap<>();
                uncovered.put("drugA", nameA);
                uncovered.put("drugB", nameB);
                uncovered.put("drugAId", pair.drugAId);
                uncovered.put("drugBId", pair.drugBId);
                uncovered.put("message", "暂无演示规则覆盖");
                uncoveredPairs.add(uncovered);
            }
        }

        // --- Build result ---
        Map<String, Object> result = new LinkedHashMap<>();

        // Drug list
        List<Map<String, Object>> drugList = medications.stream().map(med -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", med.getId());
            m.put("name", med.getName());
            m.put("dosage", med.getDosage());
            m.put("unit", med.getUnit());
            return m;
        }).toList();
        result.put("drugs", drugList);

        // Has any interaction?
        result.put("hasInteraction", !matchedRules.isEmpty());

        // Overall risk level (highest among matched)
        String overallRisk = matchedRules.stream()
                .map(r -> (String) r.get("riskLevel"))
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(this::riskOrder))
                .orElse(null);
        result.put("overallRiskLevel", overallRisk);

        // Matched rules
        result.put("matchedRules", matchedRules);

        // Uncovered pairs
        result.put("uncoveredPairs", uncoveredPairs);

        // Rule source and version
        result.put("source", "演示规则");
        result.put("ruleVersion", "1.0");

        // Disclaimer
        String disclaimer = uncoveredPairs.isEmpty()
                ? "演示规则，非医疗建议。请咨询医生或药师确认。"
                : "演示规则，非医疗建议。部分药物组合暂无演示规则覆盖，请咨询医生或药师。";
        result.put("disclaimer", disclaimer);

        // Summary text
        if (matchedRules.isEmpty()) {
            result.put("summary", "所有药物组合均暂无演示规则覆盖，建议咨询医生或药师。");
        } else {
            long highCount = matchedRules.stream().filter(r -> "high".equals(r.get("riskLevel"))).count();
            long mediumCount = matchedRules.stream().filter(r -> "medium".equals(r.get("riskLevel"))).count();
            result.put("summary", String.format("检测到 %d 条药物相互作用（高风险 %d 条，中风险 %d 条）。",
                    matchedRules.size(), highCount, mediumCount));
        }

        // --- Persist check result ---
        DrugInteractionResult record = new DrugInteractionResult();
        record.setUserId(userId);
        record.setDrugNames(medications.stream().map(Medication::getName).collect(Collectors.joining(";")));
        record.setResultData(toJson(result));
        record.setCheckedDrugIds(dedupedIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        record.setMatchedRuleIds(matchedRuleIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        record.setRuleVersion("1.0");
        record.setDisclaimer(disclaimer);
        record.setCreatedAt(LocalDateTime.now());
        drugInteractionResultMapper.insert(record);

        return result;
    }

    // --- Helper: Drug pair ---
    private static class DrugPair {
        final Long drugAId;
        final Long drugBId;
        DrugPair(Long a, Long b) { this.drugAId = a; this.drugBId = b; }
    }

    private List<DrugPair> buildPairs(List<Medication> medications) {
        List<DrugPair> pairs = new ArrayList<>();
        for (int i = 0; i < medications.size(); i++) {
            for (int j = i + 1; j < medications.size(); j++) {
                pairs.add(new DrugPair(medications.get(i).getId(), medications.get(j).getId()));
            }
        }
        return pairs;
    }

    /**
     * Resolve the standard identifier for matching.
     * Uses standard_drug_id if set, otherwise falls back to normalized drug name.
     */
    private String resolveStandardIdentifier(Medication med) {
        if (med.getStandardDrugId() != null && !med.getStandardDrugId().isBlank()) {
            return med.getStandardDrugId().trim().toLowerCase();
        }
        if (med.getStandardDrugName() != null && !med.getStandardDrugName().isBlank()) {
            return med.getStandardDrugName().trim().toLowerCase();
        }
        // Fallback: normalized drug name
        return med.getName() != null ? med.getName().trim().toLowerCase().replaceAll("\\s+", "_") : "unknown";
    }

    /**
     * Find active rule matching drug pair (both directions).
     */
    private DrugInteractionRule findRule(String idA, String idB) {
        // Try A-B
        DrugInteractionRule rule = drugInteractionRuleMapper.selectOne(
                new LambdaQueryWrapper<DrugInteractionRule>()
                        .eq(DrugInteractionRule::getDrugA, idA)
                        .eq(DrugInteractionRule::getDrugB, idB)
                        .eq(DrugInteractionRule::getActive, 1));
        if (rule != null) return rule;

        // Try B-A (order doesn't matter)
        return drugInteractionRuleMapper.selectOne(
                new LambdaQueryWrapper<DrugInteractionRule>()
                        .eq(DrugInteractionRule::getDrugA, idB)
                        .eq(DrugInteractionRule::getDrugB, idA)
                        .eq(DrugInteractionRule::getActive, 1));
    }

    private Map<String, Object> ruleToMap(DrugInteractionRule rule, String nameA, String nameB) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ruleId", rule.getId());
        map.put("drugA", nameA);
        map.put("drugB", nameB);
        map.put("riskLevel", rule.getRiskLevel());
        map.put("description", rule.getDescription());
        map.put("advice", rule.getAdvice());
        map.put("source", rule.getSource());
        return map;
    }

    private int riskOrder(String level) {
        if (level == null) return 0;
        return switch (level.toLowerCase()) {
            case "high" -> 3;
            case "medium" -> 2;
            case "low" -> 1;
            default -> 0;
        };
    }

    private Map<String, Object> buildEmptyResult(List<Medication> medications) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> drugList = medications.stream().map(med -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", med.getId());
            m.put("name", med.getName());
            m.put("dosage", med.getDosage());
            m.put("unit", med.getUnit());
            return m;
        }).toList();
        result.put("drugs", drugList);
        result.put("hasInteraction", false);
        result.put("overallRiskLevel", null);
        result.put("matchedRules", List.of());
        result.put("uncoveredPairs", List.of());
        result.put("source", "演示规则");
        result.put("ruleVersion", "1.0");
        result.put("disclaimer", "演示规则，非医疗建议。仅一种药物无法检查相互作用。");
        result.put("summary", "需要至少两种药物才能检查相互作用。");
        return result;
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    public List<Map<String, Object>> getHistory(Long userId, Long medicationId) {
        Medication medication = medicationMapper.selectOne(
                new LambdaQueryWrapper<Medication>()
                        .eq(Medication::getId, medicationId)
                        .eq(Medication::getUserId, userId)
                        .isNull(Medication::getDeletedAt));
        if (medication == null) {
            throw BusinessException.notFound("用药提醒不存在");
        }

        List<DoseRecord> records = doseRecordMapper.selectList(
                new LambdaQueryWrapper<DoseRecord>()
                        .eq(DoseRecord::getMedicationId, medicationId)
                        .eq(DoseRecord::getUserId, userId)
                        .orderByDesc(DoseRecord::getCreatedAt));

        return records.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId());
            map.put("medicationId", r.getMedicationId());
            map.put("userId", r.getUserId());
            map.put("scheduledTime", r.getScheduledTime());
            map.put("confirmedAt", r.getConfirmedAt());
            map.put("status", r.getStatus());
            map.put("createdAt", r.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> searchDrugs(Long userId, String keyword) {
        LambdaQueryWrapper<Medication> wrapper = new LambdaQueryWrapper<Medication>()
                .eq(Medication::getUserId, userId)
                .isNull(Medication::getDeletedAt);

        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Medication::getName, keyword);
        }
        wrapper.orderByDesc(Medication::getCreatedAt)
                .last("LIMIT 20");

        List<Medication> medications = medicationMapper.selectList(wrapper);
        return medications.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(Medication medication) {
        return toMap(medication, false);
    }

    private Map<String, Object> toMap(Medication medication, boolean confirmedToday) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", medication.getId());
        map.put("userId", medication.getUserId());
        map.put("memberId", medication.getMemberId());
        map.put("name", medication.getName());
        map.put("dosage", medication.getDosage());
        map.put("unit", medication.getUnit());
        map.put("instruction", medication.getInstruction());
        map.put("frequency", medication.getFrequency());
        map.put("inventory", medication.getInventory());
        map.put("times", medication.getTimes());
        map.put("startDate", medication.getStartDate());
        map.put("endDate", medication.getEndDate());
        map.put("note", medication.getNote());
        map.put("standardDrugId", medication.getStandardDrugId());
        map.put("standardDrugName", medication.getStandardDrugName());
        map.put("status", medication.getStatus());
        map.put("todayStatus", confirmedToday ? "completed" : "pending");
        map.put("createdAt", medication.getCreatedAt());
        return map;
    }

    private void validateMemberAccess(Long userId, Long memberId) {
        if (memberId == null) return;
        Long count = familyMemberMapper.selectCount(
                new LambdaQueryWrapper<FamilyMember>()
                        .eq(FamilyMember::getId, memberId)
                        .eq(FamilyMember::getUserId, userId)
                        .isNull(FamilyMember::getDeletedAt)
        );
        if (count == 0) {
            throw BusinessException.notFound("家庭成员不存在或无权访问");
        }
    }

    private Set<Long> findConfirmedToday(Long userId, List<Medication> medications) {
        if (medications.isEmpty()) {
            return Set.of();
        }
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime nextDay = startOfDay.plusDays(1);
        return doseRecordMapper.selectList(new LambdaQueryWrapper<DoseRecord>()
                        .eq(DoseRecord::getUserId, userId)
                        .in(DoseRecord::getMedicationId, medications.stream().map(Medication::getId).toList())
                        .in(DoseRecord::getStatus, List.of("confirmed", "completed"))
                        .ge(DoseRecord::getConfirmedAt, startOfDay)
                        .lt(DoseRecord::getConfirmedAt, nextDay))
                .stream()
                .map(DoseRecord::getMedicationId)
                .collect(Collectors.toSet());
    }

    private String serializeTimes(String times) {
        if (times == null || times.isBlank()) {
            return null;
        }
        return Arrays.stream(times.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
