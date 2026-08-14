package com.kangban.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagEvaluationStatsTest {

    @Test
    void reportsSuccessFailureRateAndOrderedPercentiles() {
        RagEvaluationStats stats = new RagEvaluationStats();

        stats.recordSuccess(System.nanoTime());
        stats.recordSuccess(System.nanoTime());
        stats.recordSuccess(System.nanoTime());
        stats.recordFailure(System.nanoTime());

        RagEvaluationStats.Summary summary = stats.summary();

        assertThat(summary.count()).isEqualTo(4);
        assertThat(summary.success()).isEqualTo(3);
        assertThat(summary.failures()).isEqualTo(1);
        assertThat(summary.failureRate()).isEqualTo(0.25);
        assertThat(summary.averageMs()).isGreaterThanOrEqualTo(0.0);
        assertThat(summary.p50Ms()).isLessThanOrEqualTo(summary.p95Ms());
        assertThat(summary.p95Ms()).isLessThanOrEqualTo(summary.maxMs());
    }

    @Test
    void reportsEmptyStatsWithoutInventingLatency() {
        RagEvaluationStats.Summary summary = new RagEvaluationStats().summary();

        assertThat(summary.count()).isZero();
        assertThat(summary.failureRate()).isZero();
        assertThat(summary.averageMs()).isZero();
        assertThat(summary.p50Ms()).isZero();
        assertThat(summary.p95Ms()).isZero();
        assertThat(summary.maxMs()).isZero();
    }
}
