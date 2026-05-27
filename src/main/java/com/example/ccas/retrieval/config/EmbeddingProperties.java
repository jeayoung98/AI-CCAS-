package com.example.ccas.retrieval.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "retrieval.embedding")
public record EmbeddingProperties(
        @NotBlank String model,
        @Min(1536) @Max(1536) int dimensions,
        @NotBlank String version
) {
}
