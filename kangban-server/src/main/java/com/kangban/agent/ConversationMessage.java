package com.kangban.agent;

/**
 * 可发送给模型的历史对话消息。仅允许 user/assistant 两类角色，工具消息不进入持久化记忆。
 */
public record ConversationMessage(String role, String content) {

    public ConversationMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("对话角色不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("对话内容不能为空");
        }
    }
}
