package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateKnowledgeSourceCommand(
        @NotNull KnowledgeSourceType sourceType,
        @NotBlank String title,
        String sourceOrganization,
        String sourceUrl,
        LocalDate publishedAt,
        LocalDateTime checkedAt
) {
}
