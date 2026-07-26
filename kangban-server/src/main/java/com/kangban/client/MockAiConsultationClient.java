package com.kangban.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock AI consultation client — keyword-based responses for dev/testing.
 * Activated when app.ai.provider=mock (default).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiConsultationClient implements AiConsultationClient {

    @Override
    public String consult(Long sessionId, String userContent, String patientData) {
        log.info("Mock AI consult: sessionId={}", sessionId);
        try { Thread.sleep(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        String content = userContent != null ? userContent.toLowerCase() : "";
        if (content.contains("发烧") || content.contains("发热") || content.contains("体温")) {
            return "根据您描述的发热症状，建议您：\n\n"
                    + "1. **测量体温**：建议每隔4小时测量一次体温并记录\n"
                    + "2. **物理降温**：可用温水擦拭额头、腋下等部位\n"
                    + "3. **多饮水**：保持充足水分摄入\n"
                    + "4. **休息**：保证充足休息\n\n"
                    + "⚠️ 如果体温持续超过38.5℃或伴有其他严重症状，请及时就医。\n\n"
                    + "（演示模式 — AI 建议仅供参考）";
        } else if (content.contains("头疼") || content.contains("头痛")) {
            return "头痛可能由多种原因引起，建议您：\n\n"
                    + "1. **休息**：保证充足睡眠，避免过度用眼\n"
                    + "2. **按摩**：轻轻按摩太阳穴和颈部\n"
                    + "3. **饮食**：避免过量咖啡因和酒精\n"
                    + "4. **记录**：记录头痛发作的时间和频率\n\n"
                    + "⚠️ 如果头痛剧烈、持续不缓解或伴有呕吐等症状，建议及时就医。\n\n"
                    + "（演示模式 — AI 建议仅供参考）";
        } else if (content.contains("血压") || content.contains("高血压")) {
            return "关于血压管理，建议您：\n\n"
                    + "1. **定期监测**：每天固定时间测量血压并记录\n"
                    + "2. **低盐饮食**：每日食盐摄入量不超过5克\n"
                    + "3. **适当运动**：每周至少150分钟中等强度运动\n"
                    + "4. **规律用药**：按时服用降压药物，不要随意停药\n\n"
                    + "（演示模式 — AI 建议仅供参考）";
        } else if (content.contains("血糖") || content.contains("糖尿病")) {
            return "关于血糖管理，建议您：\n\n"
                    + "1. **定期监测**：空腹血糖和餐后2小时血糖\n"
                    + "2. **控制饮食**：减少糖分和精细碳水摄入\n"
                    + "3. **适当运动**：餐后散步有助于控制血糖\n"
                    + "4. **规律用药**：按时服用降糖药物或注射胰岛素\n\n"
                    + "（演示模式 — AI 建议仅供参考）";
        } else if (content.contains("感冒") || content.contains("咳嗽")) {
            return "感冒症状通常在一周左右自行缓解，建议您：\n\n"
                    + "1. **多休息**：保证充足睡眠\n"
                    + "2. **多喝水**：温水、蜂蜜水、柠檬水\n"
                    + "3. **对症处理**：若咳嗽严重可适当使用止咳药物\n"
                    + "4. **保持空气流通**：开窗通风\n\n"
                    + "⚠️ 如果症状持续超过一周或出现高烧，建议及时就医。\n\n"
                    + "（演示模式 — AI 建议仅供参考）";
        } else {
            return "感谢您的咨询。根据您描述的情况，我有以下建议：\n\n"
                    + "1. **密切观察**：注意症状的变化情况\n"
                    + "2. **记录信息**：记录症状出现的时间、频率和程度\n"
                    + "3. **健康生活**：保持规律作息、均衡饮食和适当运动\n\n"
                    + "如果您能提供更多详细信息，我可以给出更针对性的建议。\n\n"
                    + "（演示模式 — AI 建议仅供参考）";
        }
    }

    @Override
    public boolean isMock() { return true; }
}
