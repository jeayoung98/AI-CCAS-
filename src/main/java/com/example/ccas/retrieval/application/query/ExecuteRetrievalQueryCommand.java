package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.type.InputSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExecuteRetrievalQueryCommand(
        @NotNull InputSource inputSource,
        @AssertTrue boolean maskingCompleted,
        @NotBlank String maskedText,
        @Valid @NotNull StructuredComplaint structuredComplaint
) {
}
