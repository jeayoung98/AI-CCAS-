package com.example.ccas.retrieval.embedding;

import java.util.Objects;

public record EmbeddingResult(
        float[] vector,
        String model,
        int dimensions,
        String version
) {

    public EmbeddingResult {
        Objects.requireNonNull(vector, "vector must not be null");
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        if (vector.length != dimensions) {
            throw new IllegalArgumentException("vector length must match dimensions");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        vector = vector.clone();
    }

    @Override
    public float[] vector() {
        return vector.clone();
    }
}
