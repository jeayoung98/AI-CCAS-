package com.example.ccas.retrieval.importing;

import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ImportKnowledgeSource(
        @NotBlank String sourceKey,
        @NotNull KnowledgeSourceType sourceType,
        @NotBlank String title,
        String sourceOrganization,
        String sourceUrl,
        LocalDate publishedAt,
        LocalDateTime checkedAt,
        @Valid @NotEmpty List<ImportRetrievalItem> items
) {
    public ImportKnowledgeSource {
        items = items == null ? null : List.copyOf(items);
    }
}
