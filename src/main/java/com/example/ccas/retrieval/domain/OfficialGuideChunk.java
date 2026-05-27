package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OfficialGuideChunk(
        @NotBlank String documentTitle,
        @NotBlank String sectionTitle,
        @NotBlank String chunkText,
        @NotEmpty List<@NotNull SubjectMatterCode> subjectMatters,
        @NotNull List<@NotNull RequestedActionCode> relatedActions,
        @NotNull List<@NotNull RiskSignalCode> relatedRiskSignals
) {

    public OfficialGuideChunk {
        subjectMatters = subjectMatters == null ? null : List.copyOf(subjectMatters);
        relatedActions = relatedActions == null ? null : List.copyOf(relatedActions);
        relatedRiskSignals = relatedRiskSignals == null ? null : List.copyOf(relatedRiskSignals);
    }
}
