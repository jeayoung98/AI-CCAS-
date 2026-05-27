package com.example.ccas.retrieval.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingResultTest {

    @Test
    void defensivelyCopiesVectorArray() {
        float[] original = new float[] {1.0f, 2.0f, 3.0f};
        EmbeddingResult result = new EmbeddingResult(original, "text-embedding-3-large", 3, "embed-test");

        original[0] = 9.0f;
        assertThat(result.vector()[0]).isEqualTo(1.0f);

        float[] returned = result.vector();
        returned[1] = 9.0f;
        assertThat(result.vector()[1]).isEqualTo(2.0f);
    }
}
