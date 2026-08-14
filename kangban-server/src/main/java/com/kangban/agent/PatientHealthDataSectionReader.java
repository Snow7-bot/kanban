package com.kangban.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从已授权患者快照中提取 Agent 所需的单一数据区块。
 *
 * <p>所有区块都先经过患者快照工具的权限校验，再做结构化裁剪；不会接受客户端身份字段，
 * 也不会把其他区块一并暴露给模型。</p>
 */
@Component
@RequiredArgsConstructor
public class PatientHealthDataSectionReader {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final PatientHealthSnapshotTool snapshotTool;
    private final ObjectMapper objectMapper;

    public AgentToolResult healthMetrics(AgentExecutionContext context,
                                         Map<String, Object> arguments,
                                         String toolName) {
        return readArraySection(context, arguments, toolName, "healthMetrics", "records", "metric");
    }

    public AgentToolResult activeMedications(AgentExecutionContext context,
                                             Map<String, Object> arguments,
                                             String toolName) {
        return readArraySection(context, arguments, toolName, "activeMedications", "medications", null);
    }

    public AgentToolResult recentMedicalRecords(AgentExecutionContext context,
                                                Map<String, Object> arguments,
                                                String toolName) {
        return readArraySection(context, arguments, toolName, "recentMedicalRecords", "records", null);
    }

    private AgentToolResult readArraySection(AgentExecutionContext context,
                                             Map<String, Object> arguments,
                                             String toolName,
                                             String sourceField,
                                             String resultField,
                                             String filterField) {
        AgentToolResult snapshotResult = snapshotTool.execute(context, arguments);
        if (snapshotResult.status() != AgentToolResult.Status.SUCCESS) {
            return snapshotResult.withToolName(toolName);
        }

        try {
            JsonNode root = objectMapper.readTree(snapshotResult.content());
            JsonNode source = root.path(sourceField);
            if (!source.isArray()) {
                return AgentToolResult.failed(toolName, "SNAPSHOT_FORMAT_INVALID", "患者数据暂时无法读取。");
            }

            String filterValue = filterField == null ? null : textArgument(arguments, filterField);
            List<JsonNode> selected = new ArrayList<>();
            for (JsonNode item : source) {
                if (filterValue == null || filterValue.isBlank()
                        || filterValue.equalsIgnoreCase(item.path(filterField).asText())) {
                    selected.add(item);
                }
                if (selected.size() >= limit(arguments)) {
                    break;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contextVersion", root.path("contextVersion").asText("family-agent-v2"));
            result.put("subject", root.path("subject"));
            result.put("selectedMemberId", nullableLong(root.path("selectedMemberId")));
            result.put("dataWindow", root.path("dataWindow"));
            result.put(resultField, selected);
            return AgentToolResult.success(toolName, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return AgentToolResult.failed(toolName, "SNAPSHOT_FORMAT_INVALID", "患者数据暂时无法读取。");
        }
    }

    private int limit(Map<String, Object> arguments) {
        if (arguments == null || arguments.get("limit") == null) {
            return DEFAULT_LIMIT;
        }
        try {
            int requested = Integer.parseInt(String.valueOf(arguments.get("limit")));
            return Math.max(1, Math.min(MAX_LIMIT, requested));
        } catch (NumberFormatException ignored) {
            return DEFAULT_LIMIT;
        }
    }

    private String textArgument(Map<String, Object> arguments, String name) {
        if (arguments == null || arguments.get(name) == null) {
            return null;
        }
        return String.valueOf(arguments.get(name)).trim();
    }

    private Long nullableLong(JsonNode value) {
        return value == null || value.isNull() || !value.canConvertToLong()
                ? null : value.asLong();
    }
}
