package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.OngoingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StructuredComplaintRequest(
        @NotBlank String factualSummary,
        @Valid @NotNull ComplaintContextRequest context,
        @Valid @NotEmpty List<ObservedFactRequest> observedFacts,
        @Valid @NotNull List<RiskSignalRequest> riskSignals,
        @Valid @NotEmpty List<SubjectMatterRequest> subjectMatters,
        @Valid @NotEmpty List<RequestedActionRequest> requestedActions,
        @NotNull OngoingStatus ongoingStatus,
        @NotNull List<String> missingInformation
) {
    public StructuredComplaintRequest {
        observedFacts = observedFacts == null ? null : List.copyOf(observedFacts);
        riskSignals = riskSignals == null ? null : List.copyOf(riskSignals);
        subjectMatters = subjectMatters == null ? null : List.copyOf(subjectMatters);
        requestedActions = requestedActions == null ? null : List.copyOf(requestedActions);
        missingInformation = missingInformation == null ? null : List.copyOf(missingInformation);
    }
}
