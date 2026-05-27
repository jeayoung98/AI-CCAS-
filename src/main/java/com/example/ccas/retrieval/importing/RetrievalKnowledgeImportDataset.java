package com.example.ccas.retrieval.importing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

public record RetrievalKnowledgeImportDataset(
        @NotBlank String datasetId,
        @NotBlank String description,
        boolean synthetic,
        @NotBlank String datasetVersion,
        @NotBlank String structureVersion,
        @NotBlank String embeddingVersion,
        String reviewedBy,
        LocalDateTime reviewedAt,
        @Valid @NotEmpty List<ImportKnowledgeSource> sources
) {
    public RetrievalKnowledgeImportDataset {
        sources = sources == null ? null : List.copyOf(sources);
    }
}
