package com.kangban.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QwenAiConsultationClientTest {

    @Test
    void distinguishesKnowledgeEvidenceFromMissingPatientMedication() {
        String prompt = QwenAiConsultationClient.buildSystemPrompt(
                "{\"medications\":[]}");

        assertThat(prompt)
                .contains("患者数据库没有同名记录")
                .contains("不能否定知识库资料")
                .contains("只能依据患者数据库事实")
                .contains("[资料1]")
                .contains("当前患者数据库快照");
    }
}
