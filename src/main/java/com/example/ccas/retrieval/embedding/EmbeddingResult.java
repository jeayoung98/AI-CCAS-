package com.example.ccas.retrieval.embedding;

public record EmbeddingResult(
        float[] vector,
        String model,
        int dimensions,
        String version
) {

    public EmbeddingResult {
        if (vector != null) {
            vector = vector.clone();
        }
    }

    @Override
    public float[] vector() {
        return vector == null ? null : vector.clone();
    }
}
