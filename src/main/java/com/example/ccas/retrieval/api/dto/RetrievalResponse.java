package com.example.ccas.retrieval.api.dto;

import java.util.UUID;

public record RetrievalResponse(
        UUID queryId,
        boolean riskSignalPresent,
        EvidenceDecisionResponse decision,
        RetrievalChannelsResponse channels
) {
}
