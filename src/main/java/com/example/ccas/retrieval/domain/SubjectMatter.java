package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubjectMatter(
        @NotNull SubjectMatterCode code,
        @NotBlank String evidence,
        @NotNull Certainty certainty
) {
}
