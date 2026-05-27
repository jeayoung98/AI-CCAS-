package com.example.ccas.retrieval.experiment.report;

import java.util.List;

public record GeneralComplaintSimilarityReport(
        String experimentId,
        String embeddingModel,
        String embeddingVersion,
        String searchTextVariant,
        int corpusSize,
        int excludedCount,
        List<ExcludedExperimentRecord> excludedRecords,
        List<QuerySimilarityResult> queries,
        SimilarityMetrics metrics
) {
}
