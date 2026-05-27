package com.example.ccas.retrieval.importing;

import com.example.ccas.retrieval.domain.CategoryReference;
import com.example.ccas.retrieval.domain.OfficialGuideChunk;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ImportRetrievalItem(
        @NotBlank String externalKey,
        @NotBlank String title,
        @NotNull RetrievalItemType itemType,
        @NotNull ReviewStatus reviewStatus,
        String maskedText,
        @Valid StructuredComplaint verifiedCase,
        @Valid CategoryReference categoryReference,
        @Valid OfficialGuideChunk officialGuideChunk,
        String verifiedCategoryCode,
        String verifiedRouteCode
) {
}
