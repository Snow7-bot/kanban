package com.kangban.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Agent/RAG 运行指标边界。
 *
 * <p>只写入耗时、数量、状态和受控标识，不接受问题正文、病历内容、向量或密钥。</p>
 */
@Component
public class AgentMetrics {

    private final MeterRegistry registry;

    @Autowired
    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 手动构造的单元测试使用无操作实例，避免测试必须启动 Spring。 */
    public AgentMetrics() {
        this.registry = null;
    }

    public void recordAgentRun(String outcome, long elapsedMs) {
        if (registry == null) {
            return;
        }
        String safeOutcome = tag(outcome);
        Counter.builder("kangban.agent.runs")
                .tag("outcome", safeOutcome)
                .register(registry)
                .increment();
        Timer.builder("kangban.agent.duration")
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
    }

    public void recordRagSearch(String scope, long elapsedMs, int hitCount) {
        if (registry == null) {
            return;
        }
        String safeScope = tag(scope);
        String outcome = hitCount > 0 ? "hit" : "empty";
        Counter.builder("kangban.rag.searches")
                .tag("scope", safeScope)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        Timer.builder("kangban.rag.search.duration")
                .tag("scope", safeScope)
                .register(registry)
                .record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
        DistributionSummary.builder("kangban.rag.search.hits")
                .tag("scope", safeScope)
                .register(registry)
                .record(Math.max(0, hitCount));
        if (hitCount == 0) {
            Counter.builder("kangban.rag.empty")
                    .tag("scope", safeScope)
                    .register(registry)
                    .increment();
        }
    }

    public void recordRagFailure(String scope, long elapsedMs) {
        if (registry == null) {
            return;
        }
        String safeScope = tag(scope);
        Counter.builder("kangban.rag.searches")
                .tag("scope", safeScope)
                .tag("outcome", "failure")
                .register(registry)
                .increment();
        Timer.builder("kangban.rag.search.duration")
                .tag("scope", safeScope)
                .register(registry)
                .record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
    }

    public void recordEmbedding(String provider, int batchSize, long elapsedMs, String outcome) {
        if (registry == null) {
            return;
        }
        String safeProvider = tag(provider);
        String safeOutcome = tag(outcome);
        Counter.builder("kangban.agent.embedding.calls")
                .tag("provider", safeProvider)
                .tag("outcome", safeOutcome)
                .register(registry)
                .increment();
        Timer.builder("kangban.agent.embedding.duration")
                .tag("provider", safeProvider)
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
        DistributionSummary.builder("kangban.agent.embedding.batch.size")
                .tag("provider", safeProvider)
                .register(registry)
                .record(Math.max(0, batchSize));
    }

    public void recordToolCall(String toolName, String outcome, long elapsedMs) {
        if (registry == null) {
            return;
        }
        String safeTool = tag(toolName);
        String safeOutcome = tag(outcome);
        Counter.builder("kangban.agent.tools.calls")
                .tag("tool", safeTool)
                .tag("outcome", safeOutcome)
                .register(registry)
                .increment();
        Timer.builder("kangban.agent.tools.duration")
                .tag("tool", safeTool)
                .tag("outcome", safeOutcome)
                .register(registry)
                .record(Math.max(0, elapsedMs), TimeUnit.MILLISECONDS);
    }

    private String tag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String safe = value.replaceAll("[^A-Za-z0-9_.-]", "_")
                .toLowerCase(Locale.ROOT);
        return safe.length() <= 40 ? safe : safe.substring(0, 40);
    }
}
