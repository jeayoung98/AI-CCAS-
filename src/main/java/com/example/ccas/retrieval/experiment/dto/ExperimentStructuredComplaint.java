package com.example.ccas.retrieval.experiment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentStructuredComplaint(
        String factualSummary,
        ExperimentComplaintContext context,
        List<ExperimentObservedFact> observedFacts
) {
}
