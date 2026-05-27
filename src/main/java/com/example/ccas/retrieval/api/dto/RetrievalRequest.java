package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.InputSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RetrievalRequest(
        @NotNull InputSource inputSource,
        @AssertTrue boolean maskingCompleted,
        @NotBlank String maskedText,
        @Valid @NotNull StructuredComplaintRequest structuredComplaint
) {
}
