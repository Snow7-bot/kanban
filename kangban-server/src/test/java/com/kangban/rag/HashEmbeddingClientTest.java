package com.kangban.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HashEmbeddingClientTest {

    private final HashEmbeddingClient client = new HashEmbeddingClient();

    @Test
    void sameTextProducesSameNormalizedVectorAndSimilarTextHasSignal() {
        double[] first = client.embed("血压管理建议");
        double[] same = client.embed("血压管理建议");
        double[] related = client.embed("血压记录建议");

        assertThat(first).hasSize(HashEmbeddingClient.DIMENSIONS);
        assertThat(HashEmbeddingClient.cosine(first, same)).isEqualTo(1.0);
        assertThat(HashEmbeddingClient.cosine(first, related)).isGreaterThan(0.0);
    }
}
