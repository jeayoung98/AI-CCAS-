package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.search.RetrievalCandidates;

import java.util.UUID;

public record RetrievalQueryExecutionResult(
        UUID queryId,
        boolean riskSignalPresent,
        RetrievalCandidates candidates
) {
}
