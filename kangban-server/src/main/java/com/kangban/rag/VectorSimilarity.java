package com.kangban.rag;

/** 向量相似度计算，与具体 Embedding 实现解耦。 */
final class VectorSimilarity {

    private VectorSimilarity() {
    }

    static double cosine(double[] left, double[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        double leftNorm = 0.0;
        double rightNorm = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, dot / Math.sqrt(leftNorm * rightNorm)));
    }
}
