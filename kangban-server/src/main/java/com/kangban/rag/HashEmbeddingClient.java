package com.kangban.rag;

import com.kangban.agent.AgentMetrics;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

/**
 * 本地、可重复的开发期向量化实现。生产环境应替换为受管控的 Embedding 服务。
 */
@Component
@ConditionalOnProperty(name = "app.agent.rag.embedding-provider", havingValue = "local", matchIfMissing = true)
public class HashEmbeddingClient implements EmbeddingClient {

    static final int DIMENSIONS = 128;
    private final AgentMetrics metrics;

    @Autowired
    public HashEmbeddingClient(AgentMetrics metrics) {
        this.metrics = metrics;
    }

    public HashEmbeddingClient() {
        this(new AgentMetrics());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public double[] embed(String text) {
        long startedAt = System.currentTimeMillis();
        double[] vector = new double[DIMENSIONS];
        if (text == null || text.isBlank()) {
            metrics.recordEmbedding("local", 1, System.currentTimeMillis() - startedAt, "success");
            return vector;
        }
        String normalized = text.trim().toLowerCase();
        int[] codePoints = normalized.codePoints().toArray();
        for (int i = 0; i < codePoints.length; i++) {
            add(vector, codePoints[i], 1.0);
            if (i + 1 < codePoints.length) {
                add(vector, 31 * codePoints[i] + codePoints[i + 1], 0.6);
            }
        }
        normalize(vector);
        metrics.recordEmbedding("local", 1, System.currentTimeMillis() - startedAt, "success");
        return vector;
    }

    static double cosine(double[] left, double[] right) {
        return VectorSimilarity.cosine(left, right);
    }

    private static void add(double[] vector, int hash, double weight) {
        int index = Math.floorMod(hash, vector.length);
        vector[index] += (hash & 1) == 0 ? weight : -weight;
    }

    private static void normalize(double[] vector) {
        double norm = Math.sqrt(Arrays.stream(vector).map(value -> value * value).sum());
        if (norm == 0.0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
