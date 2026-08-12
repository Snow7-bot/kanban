package com.kangban.rag;

public interface EmbeddingClient {
    double[] embed(String text);
}
