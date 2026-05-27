package com.example.ccas.retrieval.api.dto;

import java.util.List;

public record RetrievalChannelsResponse(
        List<RetrievalHitResponse> verifiedCases,
        List<RetrievalHitResponse> categoryReferences,
        List<RetrievalHitResponse> officialGuides
) {
    public RetrievalChannelsResponse {
        verifiedCases = List.copyOf(verifiedCases);
        categoryReferences = List.copyOf(categoryReferences);
        officialGuides = List.copyOf(officialGuides);
    }
}
