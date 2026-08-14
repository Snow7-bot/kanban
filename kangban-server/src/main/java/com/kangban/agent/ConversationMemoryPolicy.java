package com.kangban.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 多轮对话的统一记忆边界。
 *
 * <p>只保留用户和助手消息，并从较旧的一侧裁剪，避免长会话无限增长。工具结果只存在
 * 当前轮的工具循环中，不会进入这个列表。</p>
 */
public final class ConversationMemoryPolicy {

    private ConversationMemoryPolicy() {
    }

    public static List<ConversationMessage> prepare(List<ConversationMessage> history,
                                                     int maxMessages,
                                                     int maxTokens,
                                                     int maxMessageCharacters) {
        if (history == null || history.isEmpty() || maxMessages <= 0 || maxTokens <= 0) {
            return List.of();
        }

        List<ConversationMessage> normalized = new ArrayList<>();
        for (ConversationMessage message : history) {
            if (message == null || !isConversationRole(message.role())) {
                continue;
            }
            String content = message.content() == null ? "" : message.content().trim();
            if (content.isBlank()) {
                continue;
            }
            if (maxMessageCharacters > 0 && content.length() > maxMessageCharacters) {
                content = content.substring(0, maxMessageCharacters) + "…";
            }
            normalized.add(new ConversationMessage(message.role(), content));
        }

        List<ConversationMessage> result = new ArrayList<>();
        int estimatedTokens = 0;
        for (int index = normalized.size() - 1;
             index >= 0 && result.size() < maxMessages;
             index--) {
            ConversationMessage message = normalized.get(index);
            int messageTokens = estimateTokens(message.content());
            if (!result.isEmpty() && estimatedTokens + messageTokens > maxTokens) {
                break;
            }
            if (result.isEmpty() && messageTokens > maxTokens) {
                message = new ConversationMessage(message.role(), truncateToTokens(
                        message.content(), maxTokens));
                messageTokens = estimateTokens(message.content());
            }
            result.add(0, message);
            estimatedTokens += messageTokens;
        }
        return List.copyOf(result);
    }

    static boolean isConversationRole(String role) {
        return "user".equals(role) || "assistant".equals(role);
    }

    static int estimateTokens(String content) {
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < content.length();) {
            int codePoint = content.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (isCjk(codePoint)) {
                tokens += 1;
                asciiRun = 0;
            } else if (Character.isWhitespace(codePoint)) {
                if (asciiRun > 0) {
                    tokens += (asciiRun + 3) / 4;
                    asciiRun = 0;
                }
            } else {
                asciiRun++;
            }
        }
        if (asciiRun > 0) {
            tokens += (asciiRun + 3) / 4;
        }
        return Math.max(1, tokens);
    }

    private static String truncateToTokens(String content, int maxTokens) {
        if (estimateTokens(content) <= maxTokens) {
            return content;
        }
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < content.length();) {
            int codePoint = content.codePointAt(offset);
            int width = Character.charCount(codePoint);
            String candidate = result + new String(Character.toChars(codePoint));
            int candidateTokens = estimateTokens(candidate);
            if (candidateTokens > maxTokens) {
                break;
            }
            result.appendCodePoint(codePoint);
            offset += width;
        }
        return result.isEmpty() ? content.substring(0, Math.min(content.length(), 1)) : result.toString();
    }

    private static boolean isCjk(int codePoint) {
        return (codePoint >= 0x2E80 && codePoint <= 0x9FFF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x20000 && codePoint <= 0x3134F);
    }
}
