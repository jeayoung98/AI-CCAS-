package com.example.ccas.retrieval.experiment.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralComplaintSimilarityReportSensitiveFieldTest {

    @Test
    void reportJsonDoesNotExposeSensitiveOrOutOfScopeFields() throws Exception {
        GeneralComplaintSimilarityReport report = new GeneralComplaintSimilarityReport(
                "experiment",
                "text-embedding-3-large",
                "embed-v1-large-1536",
                "WITH_TITLE",
                2,
                0,
                List.of(),
                List.of(new QuerySimilarityResult(
                        1,
                        "label",
                        List.of(2L),
                        List.of(new SimilaritySearchHit(2, "민원 B", 0.99)),
                        true,
                        true,
                        1.0,
                        1.0
                )),
                new SimilarityMetrics(1.0, 1.0, 1.0, 1.0)
        );

        String json = new ObjectMapper().writeValueAsString(report);

        assertThat(json).doesNotContain(
                "original_content",
                "\"search_text\"",
                "\"searchText\"",
                "\"embeddingVector\"",
                "\"vector\"",
                "api-key",
                "apiKey",
                "riskSignals",
                "subjectMatters",
                "requestedActions"
        );
    }
}
