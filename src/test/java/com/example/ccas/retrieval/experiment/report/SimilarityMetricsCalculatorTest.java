package com.example.ccas.retrieval.experiment.report;

import com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityQuery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityMetricsCalculatorTest {

    private final SimilarityMetricsCalculator calculator = new SimilarityMetricsCalculator();

    @Test
    void calculatesQueryMetrics() {
        QuerySimilarityResult result = calculator.calculateQuery(
                new ExpectedSimilarityQuery(1, "query", List.of(3L, 4L)),
                List.of(
                        new SimilaritySearchHit(2, "unrelated", 0.8),
                        new SimilaritySearchHit(3, "relevant", 0.7),
                        new SimilaritySearchHit(5, "other", 0.6)
                )
        );

        assertThat(result.hitAt1()).isFalse();
        assertThat(result.hitAt3()).isTrue();
        assertThat(result.recallAt5()).isEqualTo(0.5);
        assertThat(result.reciprocalRank()).isEqualTo(0.5);
    }

    @Test
    void calculatesAggregateMetrics() {
        List<QuerySimilarityResult> results = List.of(
                new QuerySimilarityResult(1, "a", List.of(2L), List.of(), true, true, 1.0, 1.0),
                new QuerySimilarityResult(2, "b", List.of(1L), List.of(), false, true, 0.5, 0.5)
        );

        SimilarityMetrics metrics = calculator.calculateMetrics(results);

        assertThat(metrics.hitAt1()).isEqualTo(0.5);
        assertThat(metrics.hitAt3()).isEqualTo(1.0);
        assertThat(metrics.recallAt5()).isEqualTo(0.75);
        assertThat(metrics.mrr()).isEqualTo(0.75);
    }
}
