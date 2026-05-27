package com.example.ccas.retrieval.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticEvaluationPreconditionsTest {

    private final SemanticEvaluationPreconditions preconditions = new SemanticEvaluationPreconditions();

    @Test
    void rejectsHumanReviewedEvaluationUnlessExplicitlyEnabledWithApiKeyAndNonSyntheticDataset() {
        EvaluationDataset syntheticDataset = new EvaluationDataset(
                "synthetic",
                "synthetic",
                true,
                "complaint-structure-v1",
                "embed-v1-large-1536",
                "provisional-v1",
                java.util.List.of(),
                java.util.List.of(new EvaluationDatasetLoaderTestFixture().query())
        );

        assertThatThrownBy(() -> preconditions.requireHumanReviewedOpenAiEvaluation(syntheticDataset, false, "test"))
                .isInstanceOf(EvaluationDatasetException.class)
                .hasMessageContaining("explicitly enabled");
        assertThatThrownBy(() -> preconditions.requireHumanReviewedOpenAiEvaluation(syntheticDataset, true, "test"))
                .isInstanceOf(EvaluationDatasetException.class)
                .hasMessageContaining("synthetic dataset");

        EvaluationDataset humanDataset = new EvaluationDataset(
                "human",
                "human reviewed",
                false,
                "complaint-structure-v1",
                "embed-v1-large-1536",
                "provisional-v1",
                java.util.List.of(),
                java.util.List.of(new EvaluationDatasetLoaderTestFixture().query())
        );

        assertThatThrownBy(() -> preconditions.requireHumanReviewedOpenAiEvaluation(humanDataset, true, " "))
                .isInstanceOf(EvaluationDatasetException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
