package com.example.ccas.retrieval.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "retrieval.search")
public record RetrievalSearchProperties(
        @Valid @NotNull TopK topK
) {

    public record TopK(
            @Min(1) int verifiedCase,
            @Min(1) int categoryReference,
            @Min(1) int officialGuide
    ) {
    }
}
