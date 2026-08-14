package com.kangban.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 康伴医疗 Agent 的受控工具规划器。
 *
 * <p>模型不直接获得 SQL 或任意函数执行权。规划器只根据问题选择固定白名单工具，
 * 身份和患者范围始终来自服务端上下文。</p>
 */
public final class AgentToolPlanner {

    private static final int DEFAULT_LIMIT = 20;

    public List<AgentToolCall> plan(String message, int maxIterations) {
        String query = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        Map<String, AgentToolCall> calls = new LinkedHashMap<>();

        if (containsAny(query, "血压", "心率", "心跳", "血糖", "体重", "步数", "睡眠", "健康指标", "健康数据", "趋势", "测量", "异常")) {
            Map<String, Object> arguments = new LinkedHashMap<>();
            String metric = metricFor(query);
            if (metric != null) {
                arguments.put("metric", metric);
            }
            arguments.put("limit", DEFAULT_LIMIT);
            calls.put("get_health_metrics", new AgentToolCall("get_health_metrics", arguments));
        }
        if (containsAny(query, "用药", "吃药", "服药", "药物", "药品", "剂量", "漏服", "相互作用")) {
            calls.put("get_active_medications", new AgentToolCall(
                    "get_active_medications", Map.of("limit", DEFAULT_LIMIT)));
        }
        if (containsAny(query, "病历", "报告", "检查", "体检", "诊断", "医院", "医生")) {
            calls.put("get_recent_medical_records", new AgentToolCall(
                    "get_recent_medical_records", Map.of("limit", 5)));
        }
        if (calls.isEmpty()) {
            calls.put("get_patient_health_snapshot", new AgentToolCall(
                    "get_patient_health_snapshot", Map.of()));
        }

        int limit = Math.max(1, maxIterations);
        return calls.values().stream().limit(limit).toList();
    }

    private String metricFor(String query) {
        if (containsAny(query, "血压")) return "blood_pressure";
        if (containsAny(query, "心率", "心跳")) return "heart_rate";
        if (containsAny(query, "血糖")) return "blood_glucose";
        if (containsAny(query, "体重")) return "weight";
        if (containsAny(query, "步数")) return "steps";
        if (containsAny(query, "睡眠")) return "sleep";
        return null;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) return true;
        }
        return false;
    }
}
