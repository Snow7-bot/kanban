package com.kangban.agent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentMetricsTest {

    @Test
    void recordsLowCardinalityAgentRagEmbeddingAndToolMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.recordAgentRun("success", 12);
        metrics.recordRagSearch("PUBLIC", 8, 2);
        metrics.recordRagSearch("private", 4, 0);
        metrics.recordEmbedding("qwen", 16, 30, "failure");
        metrics.recordToolCall("get_health_metrics", "SUCCESS", 5);

        assertThat(registry.counter("kangban.agent.runs", "outcome", "success").count()).isEqualTo(1);
        assertThat(registry.counter("kangban.rag.searches", "scope", "public", "outcome", "hit").count())
                .isEqualTo(1);
        assertThat(registry.counter("kangban.rag.empty", "scope", "private").count()).isEqualTo(1);
        assertThat(registry.counter("kangban.agent.embedding.calls",
                "provider", "qwen", "outcome", "failure").count()).isEqualTo(1);
        assertThat(registry.counter("kangban.agent.tools.calls",
                "tool", "get_health_metrics", "outcome", "success").count()).isEqualTo(1);
        assertThat(registry.find("kangban.agent.embedding.batch.size")
                .tag("provider", "qwen").summary().count()).isEqualTo(1);
    }

    @Test
    void sanitizesMetricTagsAndDoesNotRecordFreeFormContent() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AgentMetrics metrics = new AgentMetrics(registry);

        metrics.recordToolCall("tool/name", "failure:detail", 1);

        assertThat(registry.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags().toString()).doesNotContain("原始错误"));
        assertThat(registry.find("kangban.agent.tools.calls")
                .tag("tool", "tool_name").tag("outcome", "failure_detail").counter().count())
                .isEqualTo(1);
    }
}
