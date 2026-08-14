package com.kangban.agent;

import com.kangban.common.BusinessException;
import com.kangban.service.FamilyAccessService;
import com.kangban.service.PatientHealthContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 读取当前授权患者的数据库健康快照。
 *
 * <p>患者身份只取自服务端创建的 Agent 上下文，调用参数中的身份字段会被忽略。
 * 快照复用问诊入口已有的构建逻辑，确保本人、家庭账号和家庭成员的数据范围一致。</p>
 */
@Component
@RequiredArgsConstructor
public class PatientHealthSnapshotTool implements AgentTool {

    private final PatientHealthContextService patientHealthContextService;
    private final FamilyAccessService familyAccessService;

    @Override
    public String name() {
        return "get_patient_health_snapshot";
    }

    @Override
    public String description() {
        return "读取当前授权患者最近30天健康指标、当前有效用药和最近病历摘要";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "additionalProperties", false
        );
    }

    @Override
    public AgentToolResult execute(AgentExecutionContext context, Map<String, Object> arguments) {
        if (context == null) {
            return AgentToolResult.failed(name(), "INVALID_CONTEXT", "Agent 上下文无效。");
        }
        try {
            familyAccessService.require(
                    context.actorUserId(), context.subjectUserId(), FamilyAccessService.Scope.USE_AI);
            PatientHealthContextService.Snapshot snapshot = patientHealthContextService.build(
                    context.subjectUserId(), context.memberId());
            return AgentToolResult.success(name(), snapshot.contextJson());
        } catch (BusinessException e) {
            String errorCode = e.getCode() == 403 ? "ACCESS_DENIED" : "PATIENT_SCOPE_INVALID";
            return AgentToolResult.blocked(name(), errorCode, "当前账号无权读取该患者健康数据。");
        }
    }
}
