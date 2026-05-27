package com.example.ccas.retrieval.experiment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpectedSimilarityQuery(
        long queryId,
        String label,
        List<Long> expectedRelevantIds
) {
}
