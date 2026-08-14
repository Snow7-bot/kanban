package com.kangban.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryPolicyTest {

    @Test
    void keepsRecentMessagesWithinCountAndTokenBudget() {
        List<ConversationMessage> result = ConversationMemoryPolicy.prepare(
                List.of(
                        new ConversationMessage("user", "最早的问题"),
                        new ConversationMessage("assistant", "最早的回答"),
                        new ConversationMessage("user", "最近的问题"),
                        new ConversationMessage("assistant", "最近的回答")),
                2, 20, 4000);

        assertThat(result).extracting(ConversationMessage::content)
                .containsExactly("最近的问题", "最近的回答");
    }

    @Test
    void removesToolRolesAndTruncatesAnOversizedMessage() {
        List<ConversationMessage> result = ConversationMemoryPolicy.prepare(
                List.of(
                        new ConversationMessage("tool", "患者工具原始返回，不应成为长期记忆"),
                        new ConversationMessage("user", "这是一条很长的历史问题")),
                12, 20, 6);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("user");
        assertThat(result.get(0).content()).endsWith("…");
        assertThat(result.get(0).content()).doesNotContain("工具原始返回");
    }
}
