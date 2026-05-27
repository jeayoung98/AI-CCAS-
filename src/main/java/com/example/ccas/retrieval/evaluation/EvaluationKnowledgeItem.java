package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.domain.CategoryReference;
import com.example.ccas.retrieval.domain.OfficialGuideChunk;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EvaluationKnowledgeItem(
        @NotBlank String itemKey,
        @NotNull RetrievalItemType itemType,
        @NotNull KnowledgeSourceType sourceType,
        @NotBlank String sourceTitle,
        @NotBlank String title,
        @NotNull ReviewStatus reviewStatus,
        @Valid StructuredComplaint verifiedCase,
        @Valid CategoryReference categoryReference,
        @Valid OfficialGuideChunk officialGuideChunk
) {

    @AssertTrue(message = "itemType must match exactly one payload and sourceType")
    boolean isPayloadAndSourceTypeValid() {
        if (itemType == null || sourceType == null) {
            return true;
        }
        int payloadCount = (verifiedCase == null ? 0 : 1)
                + (categoryReference == null ? 0 : 1)
                + (officialGuideChunk == null ? 0 : 1);
        if (payloadCount != 1) {
            return false;
        }
        return switch (itemType) {
            case VERIFIED_CASE -> verifiedCase != null && sourceType == KnowledgeSourceType.TEAM_VERIFIED_CASESET;
            case CATEGORY_REFERENCE -> categoryReference != null && sourceType == KnowledgeSourceType.CATEGORY_POLICY;
            case OFFICIAL_GUIDE -> officialGuideChunk != null && sourceType == KnowledgeSourceType.OFFICIAL_DOCUMENT;
        };
    }
}
