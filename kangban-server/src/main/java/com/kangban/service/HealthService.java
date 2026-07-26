package com.kangban.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangban.common.BusinessException;
import com.kangban.common.Result;
import com.kangban.dto.request.AddHealthRecordRequest;
import com.kangban.dto.request.UpdateHealthRecordRequest;
import com.kangban.entity.FamilyMember;
import com.kangban.entity.HealthRecord;
import com.kangban.mapper.FamilyMemberMapper;
import com.kangban.mapper.HealthRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthService {

    private final HealthRecordMapper healthRecordMapper;
    private final FamilyMemberMapper familyMemberMapper;

    public Result<Map<String, Object>> addRecord(Long userId, AddHealthRecordRequest request) {
        FamilyMember member = resolveMember(userId, request.getMemberId(), request.getMemberName());
        HealthRecord record = new HealthRecord();
        record.setUserId(userId);
        record.setMemberId(member != null ? member.getId() : null);
        record.setMemberName(member != null ? member.getName() : null);
        record.setMetric(request.getMetric());
        record.setValue(request.getValue());
        record.setUnit(request.getUnit());
        record.setRecordedDate(request.getRecordedDate() != null ? request.getRecordedDate() : LocalDate.now());
        record.setRecordedTime(request.getRecordedTime() != null ? request.getRecordedTime() : LocalTime.now());
        record.setNote(request.getNote());
        record.setCreatedAt(LocalDateTime.now());
        healthRecordMapper.insert(record);

        return Result.success("记录成功", toMap(record));
    }

    public void updateRecord(Long userId, Long id, UpdateHealthRecordRequest request) {
        HealthRecord record = healthRecordMapper.selectOne(
                new LambdaQueryWrapper<HealthRecord>()
                        .eq(HealthRecord::getId, id)
                        .eq(HealthRecord::getUserId, userId)
                        .isNull(HealthRecord::getDeletedAt));
        if (record == null) {
            throw BusinessException.notFound("健康记录不存在");
        }
        if (request.getValue() != null) {
            record.setValue(request.getValue());
        }
        if (request.getUnit() != null) {
            record.setUnit(request.getUnit());
        }
        if (request.getRecordedDate() != null) {
            record.setRecordedDate(request.getRecordedDate());
        }
        if (request.getRecordedTime() != null) {
            record.setRecordedTime(request.getRecordedTime());
        }
        if (request.getNote() != null) {
            record.setNote(request.getNote());
        }
        healthRecordMapper.updateById(record);
    }

    public void deleteRecord(Long userId, Long id) {
        HealthRecord record = healthRecordMapper.selectOne(
                new LambdaQueryWrapper<HealthRecord>()
                        .eq(HealthRecord::getId, id)
                        .eq(HealthRecord::getUserId, userId)
                        .isNull(HealthRecord::getDeletedAt));
        if (record == null) {
            throw BusinessException.notFound("健康记录不存在");
        }
        record.setDeletedAt(LocalDateTime.now());
        healthRecordMapper.updateById(record);
    }

    public Result<Map<String, Object>> getTrends(Long userId, String metric, Integer days, Long memberId, String legacyMember) {
        FamilyMember member = resolveMember(userId, memberId, legacyMember);
        LambdaQueryWrapper<HealthRecord> wrapper = new LambdaQueryWrapper<HealthRecord>()
                .eq(HealthRecord::getUserId, userId)
                .isNull(HealthRecord::getDeletedAt);

        if (metric != null && !metric.isEmpty()) {
            wrapper.eq(HealthRecord::getMetric, metric);
        }
        if (member != null) {
            wrapper.eq(HealthRecord::getMemberId, member.getId());
        } else {
            wrapper.isNull(HealthRecord::getMemberId);
        }
        if (days != null && days > 0) {
            LocalDate since = LocalDate.now().minus(days, ChronoUnit.DAYS);
            wrapper.ge(HealthRecord::getRecordedDate, since);
        }
        wrapper.orderByDesc(HealthRecord::getRecordedDate)
                .orderByDesc(HealthRecord::getRecordedTime);

        List<HealthRecord> records = healthRecordMapper.selectList(wrapper);

        List<Map<String, Object>> recordList = records.stream().map(this::toMap).toList();

        // Compute stats
        Map<String, Object> stats = new HashMap<>();
        if (!records.isEmpty()) {
            List<Double> numericValues = records.stream()
                    .map(r -> parseDouble(r.getValue()))
                    .filter(Objects::nonNull)
                    .toList();

            if (!numericValues.isEmpty()) {
                DoubleSummaryStatistics summary = numericValues.stream()
                        .mapToDouble(Double::doubleValue)
                        .summaryStatistics();
                stats.put("latest", records.get(0).getValue());
                stats.put("average", String.format("%.1f", summary.getAverage()));
                stats.put("peak", String.format("%.1f", summary.getMax()));
                stats.put("min", String.format("%.1f", summary.getMin()));
                stats.put("count", summary.getCount());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", recordList);
        result.put("stats", stats);
        result.put("metric", metric);
        result.put("days", days);
        result.put("memberId", member != null ? member.getId() : null);
        result.put("memberName", member != null ? member.getName() : null);
        return Result.success(result);
    }

    public Result<Map<String, Object>> getReport(Long userId, String period, Long memberId, String legacyMember) {
        FamilyMember member = resolveMember(userId, memberId, legacyMember);
        // Determine date range
        int days;
        if ("week".equalsIgnoreCase(period)) {
            days = 7;
        } else if ("month".equalsIgnoreCase(period)) {
            days = 30;
        } else if ("quarter".equalsIgnoreCase(period)) {
            days = 90;
        } else if ("year".equalsIgnoreCase(period)) {
            days = 365;
        } else {
            days = 30;
        }

        LocalDate since = LocalDate.now().minus(days, ChronoUnit.DAYS);

        LambdaQueryWrapper<HealthRecord> wrapper = new LambdaQueryWrapper<HealthRecord>()
                .eq(HealthRecord::getUserId, userId)
                .isNull(HealthRecord::getDeletedAt)
                .ge(HealthRecord::getRecordedDate, since);

        if (member != null) {
            wrapper.eq(HealthRecord::getMemberId, member.getId());
        } else {
            wrapper.isNull(HealthRecord::getMemberId);
        }

        List<HealthRecord> records = healthRecordMapper.selectList(wrapper);

        // Calculate averages per metric
        Map<String, DoubleSummaryStatistics> metricStats = records.stream()
                .filter(r -> parseDouble(r.getValue()) != null)
                .collect(Collectors.groupingBy(
                        HealthRecord::getMetric,
                        Collectors.summarizingDouble(r -> parseDouble(r.getValue()))));

        Map<String, Object> summary = new HashMap<>();
        Map<String, Object> heartRate = metricSummary(metricStats.get("heart_rate"), "bpm", 0);
        Map<String, Object> sleep = metricSummary(metricStats.get("sleep"), "小时", 1);
        Map<String, Object> steps = metricSummary(metricStats.get("steps"), "步", 0);

        summary.put("period", period);
        summary.put("days", days);
        summary.put("memberId", member != null ? member.getId() : null);
        summary.put("member", member != null ? member.getName() : "本人");
        summary.put("heartRate", heartRate);
        summary.put("sleep", sleep);
        summary.put("steps", steps);
        summary.put("recordCount", records.size());
        summary.put("dateRange", since + " - " + LocalDate.now());
        summary.put("insight", buildReportInsight(period, records.size(), metricStats));
        summary.put("generatedAt", LocalDateTime.now().toString());

        return Result.success(summary);
    }

    private Map<String, Object> metricSummary(DoubleSummaryStatistics statistics, String unit, int scale) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("unit", unit);
        if (statistics == null) {
            return summary;
        }

        String format = scale == 0 ? "%.0f" : "%.1f";
        summary.put("average", String.format(format, statistics.getAverage()));
        summary.put("min", String.format(format, statistics.getMin()));
        summary.put("max", String.format(format, statistics.getMax()));
        return summary;
    }

    private String buildReportInsight(String period, int recordCount, Map<String, DoubleSummaryStatistics> metricStats) {
        String periodLabel = "week".equalsIgnoreCase(period) ? "本周" : "本周期";
        if (recordCount == 0) {
            return periodLabel + "暂无健康指标记录，录入一次测量数据后即可生成健康总结。";
        }
        return periodLabel + "已汇总 " + recordCount + " 条健康指标记录，包含 "
                + metricStats.size() + " 类指标。健康数据仅供参考，不替代医生诊断。";
    }

    public List<Map<String, Object>> getMetrics() {
        List<Map<String, Object>> metrics = new ArrayList<>();

        Map<String, Object> bp = new HashMap<>();
        bp.put("id", "blood_pressure");
        bp.put("label", "血压");
        bp.put("unit", "mmHg");
        metrics.add(bp);

        Map<String, Object> bs = new HashMap<>();
        bs.put("id", "blood_sugar");
        bs.put("label", "血糖");
        bs.put("unit", "mmol/L");
        metrics.add(bs);

        Map<String, Object> hr = new HashMap<>();
        hr.put("id", "heart_rate");
        hr.put("label", "心率");
        hr.put("unit", "bpm");
        metrics.add(hr);

        Map<String, Object> temp = new HashMap<>();
        temp.put("id", "temperature");
        temp.put("label", "体温");
        temp.put("unit", "℃");
        metrics.add(temp);

        Map<String, Object> w = new HashMap<>();
        w.put("id", "weight");
        w.put("label", "体重");
        w.put("unit", "kg");
        metrics.add(w);

        Map<String, Object> sleep = new HashMap<>();
        sleep.put("id", "sleep");
        sleep.put("label", "睡眠");
        sleep.put("unit", "小时");
        metrics.add(sleep);

        Map<String, Object> steps = new HashMap<>();
        steps.put("id", "steps");
        steps.put("label", "步数");
        steps.put("unit", "步");
        metrics.add(steps);

        return metrics;
    }

    private Double parseDouble(String value) {
        try {
            // Handle values like "120/80" (blood pressure) - take the first number
            if (value != null && value.contains("/")) {
                return Double.parseDouble(value.split("/")[0].trim());
            }
            return value != null ? Double.parseDouble(value.trim()) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> toMap(HealthRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("userId", record.getUserId());
        map.put("memberId", record.getMemberId());
        map.put("memberName", record.getMemberName());
        map.put("metric", record.getMetric());
        map.put("value", record.getValue());
        map.put("unit", record.getUnit());
        map.put("recordedDate", record.getRecordedDate());
        map.put("recordedTime", record.getRecordedTime());
        map.put("note", record.getNote());
        map.put("createdAt", record.getCreatedAt());
        return map;
    }

    private FamilyMember resolveMember(Long userId, Long memberId, String legacyMemberName) {
        if (memberId != null) {
            FamilyMember member = familyMemberMapper.selectOne(new LambdaQueryWrapper<FamilyMember>()
                    .eq(FamilyMember::getId, memberId)
                    .eq(FamilyMember::getUserId, userId)
                    .isNull(FamilyMember::getDeletedAt));
            if (member == null) {
                throw BusinessException.notFound("家庭成员不存在");
            }
            return member;
        }
        if (legacyMemberName == null || legacyMemberName.isBlank()
                || "自己".equals(legacyMemberName) || "本人".equals(legacyMemberName)) {
            return null;
        }
        List<FamilyMember> members = familyMemberMapper.selectList(new LambdaQueryWrapper<FamilyMember>()
                .eq(FamilyMember::getUserId, userId)
                .eq(FamilyMember::getName, legacyMemberName.trim())
                .isNull(FamilyMember::getDeletedAt));
        if (members.size() != 1) {
            throw BusinessException.paramsError("请使用有效的家庭成员 ID 记录健康数据");
        }
        return members.get(0);
    }
}
