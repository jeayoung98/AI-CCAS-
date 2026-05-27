package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RiskSignal(
        @NotNull RiskSignalCode code,
        @NotBlank String evidence,
        @NotNull Certainty certainty
) {
}
