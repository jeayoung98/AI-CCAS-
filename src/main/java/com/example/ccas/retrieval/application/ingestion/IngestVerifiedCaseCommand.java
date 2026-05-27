package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record IngestVerifiedCaseCommand(
        @Positive long sourceId,
        @NotBlank String externalKey,
        @NotBlank String title,
        @NotBlank String maskedText,
        @Valid @NotNull StructuredComplaint structuredComplaint,
        String verifiedCategoryCode,
        String verifiedRouteCode,
        @NotNull ReviewStatus reviewStatus
) {
}
