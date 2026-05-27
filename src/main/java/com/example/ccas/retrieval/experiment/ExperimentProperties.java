package com.example.ccas.retrieval.experiment;

import java.nio.file.Path;

public record ExperimentProperties(
        Path input,
        Path expected,
        ExperimentSearchTextVariant variant
) {

    public static ExperimentProperties fromSystemProperties() {
        String input = System.getProperty("experiment.input");
        String expected = System.getProperty("experiment.expected");
        String variant = System.getProperty("experiment.variant", ExperimentSearchTextVariant.WITH_TITLE.name());
        return from(input, expected, variant);
    }

    public static ExperimentProperties from(String input, String expected, String variant) {
        if (isBlank(input)) {
            throw new ExperimentException("experiment.input system property is required.");
        }
        if (isBlank(expected)) {
            throw new ExperimentException("experiment.expected system property is required.");
        }
        ExperimentSearchTextVariant parsedVariant;
        try {
            parsedVariant = ExperimentSearchTextVariant.valueOf(
                    isBlank(variant) ? ExperimentSearchTextVariant.WITH_TITLE.name() : variant
            );
        } catch (IllegalArgumentException exception) {
            throw new ExperimentException("Unsupported experiment.variant: " + variant, exception);
        }
        return new ExperimentProperties(Path.of(input), Path.of(expected), parsedVariant);
    }

    public static void requireOpenAiApiKey(String apiKey) {
        if (isBlank(apiKey)) {
            throw new ExperimentException("OPENAI_API_KEY environment variable is required for semantic similarity experiment.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
