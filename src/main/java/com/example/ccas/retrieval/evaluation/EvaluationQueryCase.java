package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.InputSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EvaluationQueryCase(
        @NotBlank String queryKey,
        @NotBlank String description,
        @NotNull InputSource inputSource,
        @NotBlank String maskedText,
        @Valid @NotNull StructuredComplaint structuredComplaint,
        @NotNull List<String> expectedRelevantVerifiedCaseKeys,
        @NotNull List<String> expectedRelevantCategoryReferenceKeys,
        @NotNull List<String> expectedRelevantOfficialGuideKeys,
        @NotNull EvidenceStatus expectedEvidenceStatus,
        boolean expectedRiskSignalPresent,
        boolean noReliableSimilarCaseExpected,
        boolean ambiguityExpected
) {
    public EvaluationQueryCase {
        expectedRelevantVerifiedCaseKeys = expectedRelevantVerifiedCaseKeys == null
                ? null
                : List.copyOf(expectedRelevantVerifiedCaseKeys);
        expectedRelevantCategoryReferenceKeys = expectedRelevantCategoryReferenceKeys == null
                ? null
                : List.copyOf(expectedRelevantCategoryReferenceKeys);
        expectedRelevantOfficialGuideKeys = expectedRelevantOfficialGuideKeys == null
                ? null
                : List.copyOf(expectedRelevantOfficialGuideKeys);
    }
}
