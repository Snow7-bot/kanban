package com.kangban.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LiveMedicationRetrievalFixtureTest {

    private static final String CONTENT = "用药提醒演示规范 用药提醒可以设置每日用药时间，提醒完成后需要由用户确认本次服药。"
            + "具体药品、剂量和时间不得自行按照测试资料调整，应以医生或药师给出的处方和说明为准。";

    @Test
    void matchesReminderCompletionQuestionAboveRetrievalThreshold() {
        assertThat(KeywordRelevance.score("提醒完成后需要做什么？", CONTENT))
                .isGreaterThanOrEqualTo(0.7);
    }

    @Test
    void matchesMedicationAdjustmentQuestionAboveRetrievalThreshold() {
        assertThat(KeywordRelevance.score("用药时间能不能自行按照测试资料调整？", CONTENT))
                .isGreaterThanOrEqualTo(0.7);
    }
}
