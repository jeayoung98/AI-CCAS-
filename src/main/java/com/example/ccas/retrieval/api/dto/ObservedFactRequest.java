package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.FactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ObservedFactRequest(
        @NotNull FactType factType,
        @NotBlank String value,
        @NotBlank String evidence,
        @NotNull Certainty certainty
) {
}
