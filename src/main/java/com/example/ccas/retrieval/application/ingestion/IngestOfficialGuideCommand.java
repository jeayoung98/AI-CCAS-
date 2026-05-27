package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.domain.OfficialGuideChunk;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record IngestOfficialGuideCommand(
        @Positive long sourceId,
        @NotBlank String externalKey,
        @NotBlank String title,
        @Valid @NotNull OfficialGuideChunk officialGuideChunk,
        @NotNull ReviewStatus reviewStatus
) {
}
