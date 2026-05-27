package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CategoryReference(
        @NotBlank String categoryCode,
        @NotBlank String categoryName,
        @NotBlank String description,
        @NotEmpty List<@NotBlank String> inclusionCriteria,
        @NotNull List<@NotBlank String> exclusionCriteria,
        @NotNull List<@NotNull RiskSignalCode> relevantRiskSignals,
        @NotEmpty List<@NotNull SubjectMatterCode> subjectMatters,
        @NotEmpty List<@NotNull RequestedActionCode> supportedActions,
        boolean requiresOfficialGuide
) {

    public CategoryReference {
        inclusionCriteria = inclusionCriteria == null ? null : List.copyOf(inclusionCriteria);
        exclusionCriteria = exclusionCriteria == null ? null : List.copyOf(exclusionCriteria);
        relevantRiskSignals = relevantRiskSignals == null ? null : List.copyOf(relevantRiskSignals);
        subjectMatters = subjectMatters == null ? null : List.copyOf(subjectMatters);
        supportedActions = supportedActions == null ? null : List.copyOf(supportedActions);
    }
}
