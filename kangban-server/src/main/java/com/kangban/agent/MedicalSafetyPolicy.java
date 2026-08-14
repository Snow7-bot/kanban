package com.kangban.agent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 康伴问诊的确定性医疗安全边界。
 *
 * <p>模型提示词用于引导，下面的规则用于兜底：疑似急症不再等待模型生成；模型输出明确
 * 的诊断或处方结论时，服务端替换为转诊提示。规则不读取或记录完整病历内容。</p>
 */
public final class MedicalSafetyPolicy {

    private static final List<String> EMERGENCY_TERMS = List.of(
            "胸痛", "胸口剧痛", "呼吸困难", "喘不上气", "呼吸不上来", "意识不清",
            "昏迷", "晕厥", "抽搐", "口角歪斜", "言语不清", "单侧无力", "大出血",
            "呕血", "黑便", "喉头水肿", "严重过敏", "心肌梗死", "脑卒中", "中风",
            "过量服药", "自杀", "伤害自己"
    );

    private static final List<String> EMERGENCY_ACTION_TERMS = List.of(
            "怎么办", "需要去医院", "要紧吗", "严重吗", "现在", "突然", "突发",
            "持续", "伴随", "急救", "处理"
    );

    private static final Pattern DEFINITIVE_DIAGNOSIS = Pattern.compile(
            "(你|您)(已经|就是|患有|得了|确诊为)|诊断为|确诊为");
    private static final Pattern PRESCRIPTION_DIRECTIVE = Pattern.compile(
            "处方为|给你开药|(?:请|建议)服用|每日服用\\s*\\d+|增加剂量|减少剂量|调整剂量|停用.*药|换用.*药");

    private MedicalSafetyPolicy() {
    }

    public enum RiskLevel {
        NORMAL,
        EMERGENCY
    }

    public record Assessment(RiskLevel riskLevel, String notice) {
        public boolean isEmergency() {
            return riskLevel == RiskLevel.EMERGENCY;
        }
    }

    public static Assessment assess(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return new Assessment(RiskLevel.NORMAL, "");
        }
        boolean hasEmergencyTerm = EMERGENCY_TERMS.stream().anyMatch(normalized::contains);
        if (!hasEmergencyTerm) {
            return new Assessment(RiskLevel.NORMAL, "");
        }
        boolean asksForImmediateHelp = EMERGENCY_ACTION_TERMS.stream().anyMatch(normalized::contains);
        return new Assessment(RiskLevel.EMERGENCY, asksForImmediateHelp
                ? emergencyNotice()
                : "消息中出现了需要优先线下评估的症状。若症状正在发生或加重，请立即联系急救服务或前往最近的急诊；不要仅依赖在线问诊。");
    }

    public static String guardResponse(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }
        String content = response.trim();
        if (DEFINITIVE_DIAGNOSIS.matcher(content).find()
                || PRESCRIPTION_DIRECTIVE.matcher(content).find()) {
            return "我不能根据在线对话为您确诊、开具处方或决定药物剂量。请携带当前用药和检查资料，咨询执业医生或药师，由专业人员结合面诊、检查结果和既往史判断。";
        }
        return content;
    }

    public static String emergencyNotice() {
        return "检测到可能需要紧急处理的症状。若您或身边的人正在出现胸痛、呼吸困难、意识异常、单侧无力、大出血或其他快速加重的情况，请立即拨打 120 或前往最近的急诊。请不要等待 AI 回复，也不要自行服药或调整剂量。";
    }

    public static String promptRules() {
        return "医疗安全边界：不得输出确诊结论、处方、具体剂量调整、停药或换药指令；"
                + "不得把患者数据库事实扩展成医学结论。若本轮提供了已审核资料，只能依据这些资料并使用真实引用；"
                + "若未提供资料，不得声称回答来自某份资料、指南或数据库。"
                + "如出现胸痛、呼吸困难、意识异常、单侧无力、大出血或自伤风险，优先建议立即线下急诊；"
                + "只提供一般健康信息，不能替代医生或药师。";
    }

    private static String normalize(String message) {
        return message == null ? "" : message
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，。！？、；：:,.!?]", "");
    }
}
