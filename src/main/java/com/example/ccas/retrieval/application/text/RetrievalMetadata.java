package com.example.ccas.retrieval.application.text;

import java.util.List;

public record RetrievalMetadata(
        List<String> subjectMatterCodes,
        List<String> requestedActionCodes,
        List<String> riskSignalCodes,
        String placeType,
        String relationshipType,
        String ongoingStatus
) {

    public RetrievalMetadata {
        subjectMatterCodes = List.copyOf(subjectMatterCodes);
        requestedActionCodes = List.copyOf(requestedActionCodes);
        riskSignalCodes = List.copyOf(riskSignalCodes);
    }
}
