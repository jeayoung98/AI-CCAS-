package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RequestedActionRequest(
        @NotNull RequestedActionCode code,
        @NotBlank String evidence,
        @NotNull Certainty certainty
) {
}
