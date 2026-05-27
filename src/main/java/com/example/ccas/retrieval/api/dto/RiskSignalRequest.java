package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RiskSignalRequest(
        @NotNull RiskSignalCode code,
        @NotBlank String evidence,
        @NotNull Certainty certainty
) {
}
