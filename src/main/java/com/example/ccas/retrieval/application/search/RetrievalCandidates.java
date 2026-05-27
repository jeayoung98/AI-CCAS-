package com.example.ccas.retrieval.application.search;

import java.util.List;

public record RetrievalCandidates(
        List<RetrievalSearchHit> verifiedCases,
        List<RetrievalSearchHit> categoryReferences,
        List<RetrievalSearchHit> officialGuides
) {

    public RetrievalCandidates {
        verifiedCases = List.copyOf(verifiedCases);
        categoryReferences = List.copyOf(categoryReferences);
        officialGuides = List.copyOf(officialGuides);
    }
}
