package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequestedAction(
        @NotNull RequestedActionCode code,
        @NotBlank String evidence,
        @NotNull Certainty certainty
) {
}
