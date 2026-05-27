package com.example.ccas.retrieval.evaluation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EvaluationDataset(
        @NotBlank String datasetId,
        @NotBlank String description,
        boolean synthetic,
        @NotBlank String structureVersion,
        @NotBlank String embeddingVersion,
        @NotBlank String thresholdProfileVersion,
        @Valid @NotNull List<EvaluationKnowledgeItem> knowledgeItems,
        @Valid @NotEmpty List<EvaluationQueryCase> queries
) {
    public EvaluationDataset {
        knowledgeItems = knowledgeItems == null ? null : List.copyOf(knowledgeItems);
        queries = queries == null ? null : List.copyOf(queries);
    }
}
