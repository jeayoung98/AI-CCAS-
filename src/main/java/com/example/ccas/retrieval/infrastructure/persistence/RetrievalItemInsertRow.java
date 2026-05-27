package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import com.example.ccas.retrieval.embedding.EmbeddingResult;

import java.util.List;

public record RetrievalItemInsertRow(
        RetrievalItemType itemType,
        long sourceId,
        String externalKey,
        String title,
        String maskedText,
        String structuredPayloadJson,
        String searchText,
        List<String> subjectMatterCodes,
        List<String> requestedActionCodes,
        List<String> riskSignalCodes,
        String placeType,
        String relationshipType,
        String ongoingStatus,
        String verifiedCategoryCode,
        String verifiedRouteCode,
        ReviewStatus reviewStatus,
        boolean active,
        EmbeddingResult embeddingResult,
        String structureVersion,
        String searchTextVersion
) {
}
