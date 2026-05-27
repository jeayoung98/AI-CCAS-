package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class EvaluationRunnerServiceIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_evaluation_test")
            .withUsername("ccas")
            .withPassword("ccas-test-password");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private EvaluationDatasetLoader datasetLoader;

    @Autowired
    private EvaluationRunnerService runnerService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SyntheticEmbeddingPort syntheticEmbeddingPort;

    @TempDir
    Path tempDir;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM retrieval_decision");
        jdbcTemplate.update("DELETE FROM retrieval_hit");
        jdbcTemplate.update("DELETE FROM retrieval_query");
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");
        syntheticEmbeddingPort.reset();
    }

    @Test
    void runsSyntheticEvaluationEndToEndWithoutOpenAiCalls() throws Exception {
        EvaluationDataset dataset = datasetLoader.loadSynthetic(Path.of(
                "src/test/resources/retrieval/evaluation/synthetic/synthetic-retrieval-evaluation-v1.json"
        ));

        EvaluationReport report = runnerService.run(dataset);
        runnerService.writeJsonReport(report, tempDir);

        assertThat(report.datasetId()).isEqualTo("synthetic-retrieval-evaluation-v1");
        assertThat(report.synthetic()).isTrue();
        assertThat(report.totalQueryCount()).isEqualTo(dataset.queries().size());
        assertThat(report.thresholdProfileVersion()).isEqualTo("provisional-v1");
        assertThat(report.provisionalThreshold()).isTrue();
        assertThat(report.queryResults()).hasSize(8);
        assertThat(report.queryResults()).extracting(EvaluationQueryResult::queryKey)
                .contains("query-lost-item-synthetic", "query-ambiguous-synthetic", "query-no-match-synthetic");
        assertThat(report.evidenceStatusAccuracy()).isGreaterThan(0.0);
        assertThat(report.riskSignalPreservationRate()).isEqualTo(1.0);
        assertThat(report.noMatchRejectionAccuracy()).isEqualTo(1.0);
        assertThat(report.ambiguityDetectionAccuracy()).isEqualTo(1.0);
        assertThat(report.categoryReferenceMetrics().topK()).isEqualTo(3);
        assertThat(report.officialGuideMetrics().topK()).isEqualTo(3);
        assertThat(syntheticEmbeddingPort.callCount()).isEqualTo(dataset.knowledgeItems().size() + dataset.queries().size());

        Path reportPath = tempDir.resolve("synthetic-retrieval-evaluation-v1.json");
        assertThat(reportPath).exists();
        assertThat(Files.readString(reportPath)).contains("\"synthetic\" : true");
    }

    @TestConfiguration
    static class SyntheticEmbeddingConfig {

        @Bean
        @Primary
        SyntheticEmbeddingPort syntheticEmbeddingPort() {
            return new SyntheticEmbeddingPort();
        }
    }

    static class SyntheticEmbeddingPort implements EmbeddingPort {

        private final AtomicInteger callCount = new AtomicInteger();
        private final Map<String, float[]> vectors = Map.ofEntries(
                Map.entry("SYNTHETIC_VECTOR:ambiguous_a", vector(new float[]{0.8f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.6f})),
                Map.entry("SYNTHETIC_VECTOR:ambiguous_b", vector(new float[]{0.82f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.572f})),
                Map.entry("SYNTHETIC_VECTOR:ambiguous_q", vector(new float[]{0.81f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.586f})),
                Map.entry("SYNTHETIC_VECTOR:lost", vector(new float[]{1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})),
                Map.entry("SYNTHETIC_VECTOR:traffic", vector(new float[]{0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f})),
                Map.entry("SYNTHETIC_VECTOR:cyber", vector(new float[]{0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f})),
                Map.entry("SYNTHETIC_VECTOR:patrol", vector(new float[]{0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f})),
                Map.entry("SYNTHETIC_VECTOR:safety", vector(new float[]{0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f})),
                Map.entry("SYNTHETIC_VECTOR:novel", vector(new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f})),
                Map.entry("SYNTHETIC_VECTOR:weak", vector(new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f}))
        );

        @Override
        public EmbeddingResult embed(String searchText) {
            callCount.incrementAndGet();
            return new EmbeddingResult(vectorFor(searchText), "synthetic-fake-embedding", 1536, "embed-v1-large-1536");
        }

        void reset() {
            callCount.set(0);
        }

        int callCount() {
            return callCount.get();
        }

        private float[] vectorFor(String searchText) {
            return vectors.entrySet().stream()
                    .filter(entry -> searchText.contains(entry.getKey()))
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .map(float[]::clone)
                    .orElseThrow(() -> new IllegalArgumentException("No synthetic vector token in search_text."));
        }

        private static float[] vector(float[] seed) {
            float[] vector = new float[1536];
            System.arraycopy(seed, 0, vector, 0, seed.length);
            return vector;
        }
    }
}
