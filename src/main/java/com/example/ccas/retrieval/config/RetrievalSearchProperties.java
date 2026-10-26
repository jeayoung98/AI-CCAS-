package com.example.ccas.retrieval.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "retrieval.search")
public record RetrievalSearchProperties(
        @Valid @NotNull TopK topK,
        @DefaultValue("BASELINE") Strategy strategy,
        @DefaultValue("20") @Min(1) @Max(1000) int candidateLimit,
        @DefaultValue("0.1") @DecimalMin("0") @DecimalMax("1") double metadataWeight,
        @DefaultValue("60") @Min(1) int rrfConstant
) {
    @org.springframework.boot.context.properties.bind.ConstructorBinding
    public RetrievalSearchProperties {}

    public enum Strategy { BASELINE, EXACT, METADATA, CODE_RRF, COSINE_CODE_RRF }

    public RetrievalSearchProperties(TopK topK) {
        this(topK, Strategy.BASELINE, 20, 0.1, 60);
    }

    public record TopK(
            @Min(1) int verifiedCase,
            @Min(1) int categoryReference,
            @Min(1) int officialGuide
    ) {
    }
}
