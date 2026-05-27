package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityPairs;
import com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityQuery;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class ExpectedPairsValidator {

    public void validate(ExpectedSimilarityPairs expectedPairs, Set<Long> corpusIds) {
        if (expectedPairs == null || expectedPairs.queries() == null || expectedPairs.queries().isEmpty()) {
            throw new ExperimentException("expected-pairs.json must contain at least one query.");
        }
        for (ExpectedSimilarityQuery query : expectedPairs.queries()) {
            validateQuery(query, corpusIds);
        }
    }

    private void validateQuery(ExpectedSimilarityQuery query, Set<Long> corpusIds) {
        if (query == null) {
            throw new ExperimentException("expected-pairs.json contains a null query.");
        }
        if (query.queryId() <= 0) {
            throw new ExperimentException("Expected queryId must be positive.");
        }
        if (isBlank(query.label())) {
            throw new ExperimentException("Expected query label must not be blank.");
        }
        if (query.expectedRelevantIds() == null || query.expectedRelevantIds().isEmpty()) {
            throw new ExperimentException("Expected relevant id list must not be empty.");
        }
        if (!corpusIds.contains(query.queryId())) {
            throw new ExperimentException("Expected queryId is not in the selected corpus: " + query.queryId());
        }
        Set<Long> relevantIds = new HashSet<>(query.expectedRelevantIds());
        if (relevantIds.contains(query.queryId())) {
            throw new ExperimentException("Expected relevant ids must not contain the query id: " + query.queryId());
        }
        for (Long id : relevantIds) {
            if (id == null || !corpusIds.contains(id)) {
                throw new ExperimentException("Expected relevant id is not in the selected corpus: " + id);
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
