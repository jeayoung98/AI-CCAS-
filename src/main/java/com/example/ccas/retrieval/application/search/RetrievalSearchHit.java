package com.example.ccas.retrieval.application.search;

import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record RetrievalSearchHit(
        long itemId,
        RetrievalItemType itemType,
        String title,
        JsonNode structuredPayload,
        List<String> subjectMatterCodes,
        List<String> requestedActionCodes,
        List<String> riskSignalCodes,
        String verifiedCategoryCode,
        String verifiedRouteCode,
        double cosineSimilarity,
        int rank
) {

    public RetrievalSearchHit {
        subjectMatterCodes = List.copyOf(subjectMatterCodes);
        requestedActionCodes = List.copyOf(requestedActionCodes);
        riskSignalCodes = List.copyOf(riskSignalCodes);
    }
}
