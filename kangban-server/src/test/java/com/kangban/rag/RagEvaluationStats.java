package com.kangban.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/** 仅用于 RAG 黄金集测试的延迟和失败率统计，不记录问题正文或文档内容。 */
final class RagEvaluationStats {

    private final List<Double> durationsMs = new ArrayList<>();
    private int successCount;
    private int failureCount;

    void recordSuccess(long startedAtNanos) {
        successCount++;
        durationsMs.add(elapsedMs(startedAtNanos));
    }

    void recordFailure(long startedAtNanos) {
        failureCount++;
        durationsMs.add(elapsedMs(startedAtNanos));
    }

    Summary summary() {
        List<Double> sorted = durationsMs.stream().sorted().toList();
        int count = sorted.size();
        double total = sorted.stream().mapToDouble(Double::doubleValue).sum();
        return new Summary(count, successCount, failureCount,
                count == 0 ? 0.0 : total / count,
                percentile(sorted, 0.50), percentile(sorted, 0.95),
                sorted.isEmpty() ? 0.0 : sorted.get(sorted.size() - 1));
    }

    String format() {
        return summary().format();
    }

    static String formatCategories(Map<String, RagEvaluationStats> categoryStats) {
        Map<String, RagEvaluationStats> ordered = new LinkedHashMap<>();
        categoryStats.keySet().stream().sorted().forEach(key -> ordered.put(key, categoryStats.get(key)));
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        ordered.forEach((category, stats) -> joiner.add(category + "{" + stats.format() + "}"));
        return joiner.toString();
    }

    private static double elapsedMs(long startedAtNanos) {
        return Math.max(0.0, (System.nanoTime() - startedAtNanos) / 1_000_000.0);
    }

    private static double percentile(List<Double> sorted, double quantile) {
        if (sorted.isEmpty()) {
            return 0.0;
        }
        int index = Math.max(0, (int) Math.ceil(sorted.size() * quantile) - 1);
        return sorted.get(index);
    }

    record Summary(int count, int success, int failures, double failureRate,
                   double averageMs, double p50Ms, double p95Ms, double maxMs) {

        Summary(int count, int success, int failures, double averageMs,
                double p50Ms, double p95Ms, double maxMs) {
            this(count, success, failures, count == 0 ? 0.0 : (double) failures / count,
                    averageMs, p50Ms, p95Ms, maxMs);
        }

        String format() {
            return String.format(Locale.ROOT,
                    "count=%d success=%d failures=%d failureRate=%.3f avgMs=%.3f "
                            + "p50Ms=%.3f p95Ms=%.3f maxMs=%.3f",
                    count, success, failures, failureRate, averageMs, p50Ms, p95Ms, maxMs);
        }
    }
}
