package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class EvaluationMetricsCalculator {

    public EvaluationReport calculate(
            EvaluationDataset dataset,
            boolean provisionalThreshold,
            List<EvaluationQueryResult> queryResults,
            int verifiedCaseTopK,
            int categoryReferenceTopK,
            int officialGuideTopK
    ) {
        return new EvaluationReport(
                dataset.datasetId(),
                dataset.synthetic(),
                dataset.embeddingVersion(),
                dataset.thresholdProfileVersion(),
                provisionalThreshold,
                dataset.queries().size(),
                channelMetrics(dataset, queryResults, Channel.VERIFIED_CASE, verifiedCaseTopK),
                channelMetrics(dataset, queryResults, Channel.CATEGORY_REFERENCE, categoryReferenceTopK),
                channelMetrics(dataset, queryResults, Channel.OFFICIAL_GUIDE, officialGuideTopK),
                evidenceStatusAccuracy(queryResults),
                riskSignalPreservationRate(dataset, queryResults),
                noMatchRejectionAccuracy(dataset, queryResults),
                ambiguityDetectionAccuracy(dataset, queryResults),
                queryResults
        );
    }

    private ChannelMetrics channelMetrics(
            EvaluationDataset dataset,
            List<EvaluationQueryResult> results,
            Channel channel,
            int topK
    ) {
        double recallSum = 0.0;
        int recallCount = 0;
        double precisionSum = 0.0;
        int precisionCount = 0;
        double mrrSum = 0.0;
        int mrrCount = 0;

        for (int index = 0; index < dataset.queries().size(); index++) {
            EvaluationQueryCase query = dataset.queries().get(index);
            EvaluationQueryResult result = results.get(index);
            List<String> expected = expectedKeys(query, channel);
            List<String> returned = returnedKeys(result, channel);

            if (!expected.isEmpty()) {
                Set<String> expectedSet = Set.copyOf(expected);
                long hitCount = returned.stream().filter(expectedSet::contains).count();
                recallSum += hitCount / (double) expected.size();
                recallCount++;

                Integer firstRelevantRank = firstRelevantRank(returned, expectedSet);
                mrrSum += firstRelevantRank == null ? 0.0 : 1.0 / firstRelevantRank;
                mrrCount++;
            }

            if (!returned.isEmpty()) {
                Set<String> expectedSet = Set.copyOf(expected);
                long hitCount = returned.stream().filter(expectedSet::contains).count();
                precisionSum += hitCount / (double) returned.size();
                precisionCount++;
            }
        }

        int evaluatedQueryCount = Math.max(recallCount, precisionCount);
        return new ChannelMetrics(
                averageOrNull(recallSum, recallCount),
                averageOrNull(precisionSum, precisionCount),
                averageOrNull(mrrSum, mrrCount),
                evaluatedQueryCount,
                topK
        );
    }

    private double evidenceStatusAccuracy(List<EvaluationQueryResult> results) {
        long matched = results.stream().filter(EvaluationQueryResult::evidenceStatusMatched).count();
        return matched / (double) results.size();
    }

    private Double riskSignalPreservationRate(EvaluationDataset dataset, List<EvaluationQueryResult> results) {
        int expectedCount = 0;
        int preservedCount = 0;
        for (int i = 0; i < dataset.queries().size(); i++) {
            if (dataset.queries().get(i).expectedRiskSignalPresent()) {
                expectedCount++;
                if (results.get(i).actualRiskSignalPresent()) {
                    preservedCount++;
                }
            }
        }
        return averageOrNull(preservedCount, expectedCount);
    }

    private Double noMatchRejectionAccuracy(EvaluationDataset dataset, List<EvaluationQueryResult> results) {
        int expectedCount = 0;
        int rejectedCount = 0;
        for (int i = 0; i < dataset.queries().size(); i++) {
            if (dataset.queries().get(i).noReliableSimilarCaseExpected()) {
                expectedCount++;
                if (results.get(i).actualEvidenceStatus() != EvidenceStatus.CASE_AND_REFERENCE_SUPPORTED) {
                    rejectedCount++;
                }
            }
        }
        return averageOrNull(rejectedCount, expectedCount);
    }

    private Double ambiguityDetectionAccuracy(EvaluationDataset dataset, List<EvaluationQueryResult> results) {
        int expectedCount = 0;
        int detectedCount = 0;
        for (int i = 0; i < dataset.queries().size(); i++) {
            if (dataset.queries().get(i).ambiguityExpected()) {
                expectedCount++;
                if (results.get(i).actualEvidenceStatus() == EvidenceStatus.AMBIGUOUS) {
                    detectedCount++;
                }
            }
        }
        return averageOrNull(detectedCount, expectedCount);
    }

    private List<String> expectedKeys(EvaluationQueryCase query, Channel channel) {
        return switch (channel) {
            case VERIFIED_CASE -> query.expectedRelevantVerifiedCaseKeys();
            case CATEGORY_REFERENCE -> query.expectedRelevantCategoryReferenceKeys();
            case OFFICIAL_GUIDE -> query.expectedRelevantOfficialGuideKeys();
        };
    }

    private List<String> returnedKeys(EvaluationQueryResult result, Channel channel) {
        return switch (channel) {
            case VERIFIED_CASE -> result.returnedVerifiedCaseKeys();
            case CATEGORY_REFERENCE -> result.returnedCategoryReferenceKeys();
            case OFFICIAL_GUIDE -> result.returnedOfficialGuideKeys();
        };
    }

    private Integer firstRelevantRank(List<String> returned, Set<String> expected) {
        for (int index = 0; index < returned.size(); index++) {
            if (expected.contains(returned.get(index))) {
                return index + 1;
            }
        }
        return null;
    }

    private Double averageOrNull(double numerator, int denominator) {
        if (denominator == 0) {
            return null;
        }
        return numerator / denominator;
    }

    private enum Channel {
        VERIFIED_CASE,
        CATEGORY_REFERENCE,
        OFFICIAL_GUIDE
    }
}
