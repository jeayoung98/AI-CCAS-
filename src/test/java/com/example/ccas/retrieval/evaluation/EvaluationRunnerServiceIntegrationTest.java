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
import java.util.List;
import java.util.LinkedHashMap;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired private RetrievalReplayExperiment replay;
    @Autowired private ObjectMapper objectMapper;

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

    @Test
    void comparesFrozenSyntheticEmbeddingsWithoutAdditionalEmbeddingCalls() throws Exception {
        var dataset = datasetLoader.loadSynthetic(Path.of(
                "src/test/resources/retrieval/evaluation/synthetic/synthetic-retrieval-evaluation-v1.json"));
        runnerService.run(dataset);
        var cache = new LinkedHashMap<String, EmbeddingResult>();
        dataset.queries().forEach(q -> cache.put(q.queryKey(),
                syntheticEmbeddingPort.embed(new SearchTextBuilder().buildForComplaint(q.structuredComplaint()))));
        int calls = syntheticEmbeddingPort.callCount();
        var k = new RetrievalSearchProperties.TopK(5, 3, 3);
        var report = replay.run(dataset, cache, List.of(
                new RetrievalSearchProperties(k),
                new RetrievalSearchProperties(k, RetrievalSearchProperties.Strategy.EXACT, 20, 0, 60),
                new RetrievalSearchProperties(k, RetrievalSearchProperties.Strategy.METADATA, 20, 0, 60),
                new RetrievalSearchProperties(k, RetrievalSearchProperties.Strategy.METADATA, 20, .1, 60),
                new RetrievalSearchProperties(k, RetrievalSearchProperties.Strategy.METADATA, 20, .3, 60),
                new RetrievalSearchProperties(k, RetrievalSearchProperties.Strategy.CODE_RRF, 20, 0, 60),
                new RetrievalSearchProperties(k, RetrievalSearchProperties.Strategy.COSINE_CODE_RRF, 20, .05, 60)
        ), 3, 10);
        assertThat(syntheticEmbeddingPort.callCount()).isEqualTo(calls);
        assertThat(report.variants()).hasSize(7);
        assertThat(report.variants().get(6).metrics().evidenceStatusAccuracy()).isEqualTo(1);
        assertThat(report.variants().get(6).ambiguity().recall()).isEqualTo(1);
        assertThat(report.variants().get(0).metrics()).isEqualTo(report.variants().get(1).metrics());
        assertThat(report.variants().get(0).metrics()).isEqualTo(report.variants().get(2).metrics());
        assertThat(report.variants()).allSatisfy(v -> {
            assertThat(v.metrics().riskSignalPreservationRate()).isEqualTo(1);
            assertThat(v.latencySampleCount()).isEqualTo(80);
            assertThat(v.retrievalP95Ms()).isGreaterThanOrEqualTo(v.retrievalP50Ms());
        });
        var mismatchedCache = new LinkedHashMap<>(cache);
        var firstQuery = dataset.queries().getFirst().queryKey();
        var firstEmbedding = cache.get(firstQuery);
        mismatchedCache.put(firstQuery, new EmbeddingResult(firstEmbedding.vector(), "different-model", 1536, firstEmbedding.version()));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> replay.run(dataset, mismatchedCache,
                List.of(new RetrievalSearchProperties(k)), 0, 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Corpus/query embedding model mismatch");
        Path output = Path.of("build/reports/retrieval/synthetic-comparison.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
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
