package com.example.ccas.retrieval.experiment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExperimentPropertiesTest {

    @Test
    void parsesRequiredSystemPropertyValues() {
        ExperimentProperties properties = ExperimentProperties.from("input.json", "expected.json", "WITHOUT_TITLE");

        assertThat(properties.input().toString()).isEqualTo("input.json");
        assertThat(properties.expected().toString()).isEqualTo("expected.json");
        assertThat(properties.variant()).isEqualTo(ExperimentSearchTextVariant.WITHOUT_TITLE);
    }

    @Test
    void rejectsMissingOpenAiApiKeyBeforeRealExperiment() {
        assertThatThrownBy(() -> ExperimentProperties.requireOpenAiApiKey(null))
                .isInstanceOf(ExperimentException.class)
                .hasMessageContaining("OPENAI_API_KEY");
        assertThatThrownBy(() -> ExperimentProperties.requireOpenAiApiKey(" "))
                .isInstanceOf(ExperimentException.class)
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
