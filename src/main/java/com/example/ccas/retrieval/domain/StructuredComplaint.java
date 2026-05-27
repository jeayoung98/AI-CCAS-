package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.OngoingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StructuredComplaint(
        @NotBlank String factualSummary,
        @Valid @NotNull ComplaintContext context,
        @Valid @NotEmpty List<ObservedFact> observedFacts,
        @Valid @NotNull List<RiskSignal> riskSignals,
        @Valid @NotEmpty List<SubjectMatter> subjectMatters,
        @Valid @NotEmpty List<RequestedAction> requestedActions,
        @NotNull OngoingStatus ongoingStatus,
        @NotNull List<String> missingInformation
) {

    public StructuredComplaint {
        observedFacts = observedFacts == null ? null : List.copyOf(observedFacts);
        riskSignals = riskSignals == null ? null : List.copyOf(riskSignals);
        subjectMatters = subjectMatters == null ? null : List.copyOf(subjectMatters);
        requestedActions = requestedActions == null ? null : List.copyOf(requestedActions);
        missingInformation = missingInformation == null ? null : List.copyOf(missingInformation);
    }
}
