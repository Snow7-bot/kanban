package com.kangban.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 读取当前授权患者的健康指标。 */
@Component
@RequiredArgsConstructor
public class HealthMetricsTool implements AgentTool {

    private final PatientHealthDataSectionReader reader;

    @Override
    public String name() {
        return "get_health_metrics";
    }

    @Override
    public String description() {
        return "读取当前授权患者最近30天健康指标，可按 metric 和 limit 过滤";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "metric", Map.of("type", "string", "description", "指标名称，如 blood_pressure、heart_rate、blood_glucose"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)
                ),
                "additionalProperties", false
        );
    }

    @Override
    public AgentToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
        return reader.healthMetrics(context, arguments, name());
    }
}
