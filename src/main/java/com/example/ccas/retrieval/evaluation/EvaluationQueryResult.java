package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.domain.type.EvidenceStatus;

import java.util.List;

public record EvaluationQueryResult(
        String queryKey,
        EvidenceStatus expectedEvidenceStatus,
        EvidenceStatus actualEvidenceStatus,
        boolean evidenceStatusMatched,
        boolean expectedRiskSignalPresent,
        boolean actualRiskSignalPresent,
        List<String> returnedVerifiedCaseKeys,
        List<String> returnedCategoryReferenceKeys,
        List<String> returnedOfficialGuideKeys
) {
    public EvaluationQueryResult {
        returnedVerifiedCaseKeys = List.copyOf(returnedVerifiedCaseKeys);
        returnedCategoryReferenceKeys = List.copyOf(returnedCategoryReferenceKeys);
        returnedOfficialGuideKeys = List.copyOf(returnedOfficialGuideKeys);
    }
}
