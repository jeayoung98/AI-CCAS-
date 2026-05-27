package com.example.ccas.retrieval.experiment.report;

public record SimilaritySearchHit(
        long id,
        String title,
        double similarity
) {
}
