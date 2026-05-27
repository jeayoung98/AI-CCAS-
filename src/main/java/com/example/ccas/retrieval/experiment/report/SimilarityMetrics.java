package com.example.ccas.retrieval.experiment.report;

public record SimilarityMetrics(
        double hitAt1,
        double hitAt3,
        double recallAt5,
        double mrr
) {
}
