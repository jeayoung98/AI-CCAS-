package com.example.ccas.retrieval.evaluation;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PublicPoolExperimentMainTest {
    @Test void queryCountDefaultsToLegacyAndAllowsExplicitPolicePool() {
        String prior=System.getProperty("pool.expectedCount");
        try {
            System.clearProperty("pool.expectedCount");
            assertThat(PublicPoolExperimentMain.expectedQueryCount()).isEqualTo(500);
            System.setProperty("pool.expectedCount","55");
            assertThat(PublicPoolExperimentMain.expectedQueryCount()).isEqualTo(55);
            System.setProperty("pool.expectedCount","0");
            assertThatThrownBy(PublicPoolExperimentMain::expectedQueryCount).isInstanceOf(IllegalArgumentException.class);
        } finally {
            if(prior==null) System.clearProperty("pool.expectedCount"); else System.setProperty("pool.expectedCount",prior);
        }
    }
    @Test void changedLabelsOrIncompleteLockAreRejected() throws Exception {
        var lock=PublicPoolExperimentMain.JSON.readTree("{\"benchmarkId\":\"frozen-v1\",\"inputs\":{\"queries\":{\"sha256\":\"q\"},\"qrels\":{\"sha256\":\"r\"}}}");
        assertThat(PublicPoolExperimentMain.verifyBenchmark(lock,Map.of("queries","q","qrels","r"))).isEqualTo("frozen-v1");
        assertThatThrownBy(() -> PublicPoolExperimentMain.verifyBenchmark(lock,Map.of("queries","q","qrels","changed"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicPoolExperimentMain.verifyBenchmark(lock,Map.of("queries","q"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void newCategoryCannotSilentlyDefaultToLegacyGrievanceCode(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var corpus=PublicPoolExperimentMain.JSON.readTree(java.nio.file.Path.of("tools/public_complaints/relevance_documents_v3.json").toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode)corpus.path("documents").get(7).path("experimentalMetadata")).remove("categoryCode");
        var file=temp.resolve("invalid.json");PublicPoolExperimentMain.JSON.writeValue(file.toFile(),corpus);
        assertThatThrownBy(() -> PublicPoolExperimentMain.documents(file,new com.example.ccas.retrieval.application.text.SearchTextBuilder())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expandedCorpusUsesExplicitMetadataWithoutChangingOldDocuments() throws Exception {
        var old=PublicPoolExperimentMain.documents(java.nio.file.Path.of("tools/public_complaints/relevance_documents_v2.json"),new com.example.ccas.retrieval.application.text.SearchTextBuilder());
        var expanded=PublicPoolExperimentMain.documents(java.nio.file.Path.of("tools/public_complaints/relevance_documents_v3.json"),new com.example.ccas.retrieval.application.text.SearchTextBuilder());
        assertThat(expanded).hasSize(20);
        assertThat(expanded.subList(0,7)).isEqualTo(old);
        assertThat(expanded.stream().filter(d -> d.type().name().equals("CATEGORY_REFERENCE"))).hasSize(4);
        assertThatThrownBy(() -> PublicPoolExperimentMain.enumCodes(PublicPoolExperimentMain.JSON.readTree("[\"NOT_A_CODE\"]"), com.example.ccas.retrieval.domain.type.SubjectMatterCode.class)).isInstanceOf(IllegalArgumentException.class);
    }
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
