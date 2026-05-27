package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.DecisionReasonCode;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;

import java.util.List;

public record EvidenceDecisionResponse(
        EvidenceStatus evidenceStatus,
        boolean provisionalThreshold,
        String thresholdProfileVersion,
        ScoreSummaryResponse scores,
        List<DecisionReasonCode> reasonCodes
) {
    public EvidenceDecisionResponse {
        reasonCodes = List.copyOf(reasonCodes);
    }
}
