package com.example.ccas.retrieval.experiment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExperimentObservedFact(
        String value
) {
}
