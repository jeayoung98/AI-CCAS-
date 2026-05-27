package com.example.ccas.retrieval.evaluation;

import java.util.List;

public record EvaluationReport(
        String datasetId,
        boolean synthetic,
        String embeddingVersion,
        String thresholdProfileVersion,
        boolean provisionalThreshold,
        int totalQueryCount,
        ChannelMetrics verifiedCaseMetrics,
        ChannelMetrics categoryReferenceMetrics,
        ChannelMetrics officialGuideMetrics,
        double evidenceStatusAccuracy,
        Double riskSignalPreservationRate,
        Double noMatchRejectionAccuracy,
        Double ambiguityDetectionAccuracy,
        List<EvaluationQueryResult> queryResults
) {
    public EvaluationReport {
        queryResults = List.copyOf(queryResults);
    }
}
