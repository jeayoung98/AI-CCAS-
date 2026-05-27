package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.HitRejectionReason;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record RetrievalHitResponse(
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
        int rank,
        boolean passedChannelCutoff,
        HitRejectionReason rejectionReason
) {
    public RetrievalHitResponse {
        subjectMatterCodes = List.copyOf(subjectMatterCodes);
        requestedActionCodes = List.copyOf(requestedActionCodes);
        riskSignalCodes = List.copyOf(riskSignalCodes);
    }
}
