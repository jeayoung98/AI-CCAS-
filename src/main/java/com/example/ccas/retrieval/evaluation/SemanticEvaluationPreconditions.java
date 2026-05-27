package com.example.ccas.retrieval.evaluation;

import org.springframework.stereotype.Component;

@Component
public class SemanticEvaluationPreconditions {

    public void requireHumanReviewedOpenAiEvaluation(EvaluationDataset dataset, boolean enabled, String apiKey) {
        if (!enabled) {
            throw new EvaluationDatasetException("Human-reviewed semantic evaluation must be explicitly enabled.");
        }
        if (dataset.synthetic()) {
            throw new EvaluationDatasetException("Human-reviewed semantic evaluation cannot run with synthetic dataset.");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new EvaluationDatasetException("OPENAI_API_KEY is required for human-reviewed semantic evaluation.");
        }
        if (dataset.queries().isEmpty()) {
            throw new EvaluationDatasetException("Human-reviewed semantic evaluation requires at least one query.");
        }
    }
}
