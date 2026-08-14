package com.kangban.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 读取当前授权患者的有效用药。 */
@Component
@RequiredArgsConstructor
public class ActiveMedicationsTool implements AgentTool {

    private final PatientHealthDataSectionReader reader;

    @Override
    public String name() {
        return "get_active_medications";
    }

    @Override
    public String description() {
        return "读取当前授权患者的当前有效用药，可用 limit 限制返回数量";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)
                ),
                "additionalProperties", false
        );
    }

    @Override
    public AgentToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
        return reader.activeMedications(context, arguments, name());
    }
}
