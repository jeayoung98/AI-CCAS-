package com.example.ccas.retrieval.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PublicPoolExperimentMainTest {
    @Test void metricsRespectFixedKNoMatchAndUniqueHits() {
        var result=PublicPoolExperimentMain.metrics(List.of(List.of("a","a","b","c"),List.of("x")),
            List.of(Set.of("a","c"),Set.of()),3);
        assertThat(result.queries()).isEqualTo(2);
        assertThat(result.relevantQueries()).isEqualTo(1);
        assertThat(result.recallAtK()).isEqualTo(0.5);
        assertThat(result.precisionAtK()).isCloseTo(1.0/6,within(0.00001));
        assertThat(result.mrr()).isEqualTo(1.0);
    }
    @Test void undefinedPositiveMetricsRemainNull() {
        var result=PublicPoolExperimentMain.metrics(List.of(List.of("a")),List.of(Set.of()),3);
        assertThat(result.recallAtK()).isNull();assertThat(result.mrr()).isNull();assertThat(result.precisionAtK()).isZero();
    }
    @Test void cosineUsesRealVectorNorms() {
        assertThat(PublicPoolExperimentMain.cosine(new float[]{2,0},new float[]{3,3})).isCloseTo(1/Math.sqrt(2),within(0.000001));
        assertThatThrownBy(() -> PublicPoolExperimentMain.validateVector(new float[]{Float.NaN,1},2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicPoolExperimentMain.validateVector(new float[]{0,0},2)).isInstanceOf(IllegalArgumentException.class);
    }
}
