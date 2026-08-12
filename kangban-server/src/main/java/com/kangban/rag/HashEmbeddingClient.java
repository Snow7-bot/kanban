package com.kangban.rag;

import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 本地、可重复的开发期向量化实现。生产环境应替换为受管控的 Embedding 服务。
 */
@Component
public class HashEmbeddingClient implements EmbeddingClient {

    static final int DIMENSIONS = 128;

    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        if (text == null || text.isBlank()) {
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
        return vector;
    }

    static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return Math.max(0.0, Math.min(1.0, dot));
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
