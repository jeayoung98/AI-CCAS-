package com.example.ccas.retrieval.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "retrieval.evaluation")
public record RetrievalEvaluationProperties(
        @NotBlank String thresholdProfileVersion,
        boolean provisional,
        @DecimalMin("0.0") @DecimalMax("1.0") double caseMinSimilarity,
        @DecimalMin("0.0") @DecimalMax("1.0") double categoryMinSimilarity,
        @DecimalMin("0.0") @DecimalMax("1.0") double guideMinSimilarity,
        @DecimalMin("0.0") @DecimalMax("1.0") double categoryMarginMin,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0") double caseAgreementMin
) {
}
