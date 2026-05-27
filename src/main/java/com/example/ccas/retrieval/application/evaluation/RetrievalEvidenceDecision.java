package com.example.ccas.retrieval.application.evaluation;

import com.example.ccas.retrieval.domain.type.DecisionReasonCode;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;

import java.util.List;

public record RetrievalEvidenceDecision(
        EvidenceStatus evidenceStatus,
        boolean riskSignalPresent,
        Double bestCaseScore,
        Double bestCategoryScore,
        Double bestGuideScore,
        Double categoryMargin,
        Double caseCategoryAgreement,
        List<DecisionReasonCode> decisionReasonCodes,
        List<RetrievalHitAssessment> hitAssessments,
        String thresholdProfileVersion,
        boolean provisionalThreshold
) {
    public RetrievalEvidenceDecision {
        decisionReasonCodes = List.copyOf(decisionReasonCodes);
        hitAssessments = List.copyOf(hitAssessments);
    }
}
