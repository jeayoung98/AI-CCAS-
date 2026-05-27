package com.example.ccas.retrieval.experiment.report;

import java.util.List;

public record QuerySimilarityResult(
        long queryId,
        String label,
        List<Long> expectedRelevantIds,
        List<SimilaritySearchHit> top5,
        boolean hitAt1,
        boolean hitAt3,
        double recallAt5,
        double reciprocalRank
) {
}
