package com.kangban.rag;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;

/** 开发期 JDBC 检索使用的轻量关键词相关度计算。 */
final class KeywordRelevance {

    private static final List<String> QUERY_STOP_PHRASES = List.of(
            "请根据知识库资料", "根据知识库资料", "知识库资料", "给出资料引用", "资料引用",
            "告诉我", "请说明", "请问", "请", "什么时候", "什么时间", "几点", "何时",
            "有没有", "是否", "能不能", "能否", "可不可以", "可以吗", "怎么", "怎样", "如何",
            "怎么操作", "如何操作", "怎么使用", "如何使用", "有什么作用", "什么作用",
            "为什么", "哪个", "哪些", "多少", "什么", "吗", "呢", "吧"
    );

    private KeywordRelevance() {
    }

    static double score(String query, String content) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedContent = content == null ? "" : content.toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank() || normalizedContent.isBlank()) {
            return 0.0;
        }
        if (normalizedContent.contains(normalizedQuery)) {
            return 1.0;
        }

        String meaningfulQuery = stripQuestionPhrases(normalizedQuery);
        if (meaningfulQuery.isBlank()) {
            return 0.0;
        }
        long total = meaningfulQuery.codePoints()
                .filter(Character::isLetterOrDigit)
                .distinct()
                .count();
        if (total == 0) {
            return 0.0;
        }
        long matched = meaningfulQuery.codePoints()
                .filter(Character::isLetterOrDigit)
                .distinct()
                .filter(point -> normalizedContent.indexOf(point) >= 0)
                .count();
        return (double) matched / total;
    }

    private static String stripQuestionPhrases(String query) {
        String result = query;
        for (String phrase : QUERY_STOP_PHRASES.stream()
                .sorted(Comparator.comparingInt(String::length).reversed()).toList()) {
            result = result.replace(phrase, "");
        }
        return result;
    }
}
