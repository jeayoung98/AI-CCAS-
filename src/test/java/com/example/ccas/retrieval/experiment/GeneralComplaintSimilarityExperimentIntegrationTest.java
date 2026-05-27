package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.experiment.report.GeneralComplaintSimilarityReport;
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

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class GeneralComplaintSimilarityExperimentIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_experiment_test")
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
    private GeneralComplaintSimilarityExperimentService service;

    @Autowired
    private CountingFakeEmbeddingPort embeddingPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void reset() {
        embeddingPort.reset();
    }

    @Test
    void runsSyntheticSimilarityExperimentWithFakeEmbeddingAndExperimentTableOnly() throws Exception {
        Path input = tempDir.resolve("output.json");
        Path expected = tempDir.resolve("expected-pairs.json");
        Files.writeString(input, """
                [
                  {
                    "id": 1,
                    "title": "버스 정류장 A",
                    "ok": true,
                    "original_content": "must not be used",
                    "dept": "must not be used",
                    "structured": {
                      "factualSummary": "버스 정류장 동선 개선 요청",
                      "context": {"placeType": "ROAD", "timePattern": "ONGOING"},
                      "observedFacts": [{"value": "정류장 대기 공간 부족"}]
                    }
                  },
                  {
                    "id": 2,
                    "title": "버스 정류장 B",
                    "ok": true,
                    "structured": {
                      "factualSummary": "버스 정류장 위치 조정 요청",
                      "context": {"placeType": "ROAD", "timePattern": "ONGOING"},
                      "observedFacts": [{"value": "정류장 동선 비효율"}]
                    }
                  },
                  {
                    "id": 3,
                    "title": "방역 민원 C",
                    "ok": true,
                    "structured": {
                      "factualSummary": "해충 방역 요청",
                      "context": {"placeType": "PUBLIC_FACILITY", "timePattern": "REPEATED"},
                      "observedFacts": [{"value": "해충 증가"}]
                    }
                  },
                  {
                    "id": 4,
                    "title": "제외 항목",
                    "ok": false,
                    "structured": {
                      "factualSummary": "제외",
                      "context": {"placeType": "ROAD", "timePattern": "ONGOING"},
                      "observedFacts": [{"value": "제외"}]
                    }
                  }
                ]
                """);
        Files.writeString(expected, """
                {
                  "queries": [
                    {"queryId": 1, "label": "bus stop", "expectedRelevantIds": [2]}
                  ]
                }
                """);

        GeneralComplaintSimilarityReport report = service.run(new ExperimentProperties(
                input,
                expected,
                ExperimentSearchTextVariant.WITH_TITLE
        ));

        assertThat(report.corpusSize()).isEqualTo(3);
        assertThat(report.excludedCount()).isEqualTo(1);
        assertThat(embeddingPort.callCount()).isEqualTo(3);
        assertThat(report.queries()).hasSize(1);
        assertThat(report.queries().getFirst().top5()).extracting(hit -> hit.id())
                .doesNotContain(1L);
        assertThat(report.queries().getFirst().top5().getFirst().id()).isEqualTo(2L);
        assertThat(report.metrics().hitAt1()).isEqualTo(1.0);
        assertThat(count("experiment_general_complaint_item")).isEqualTo(3);
        assertThat(count("retrieval_item")).isZero();
        assertThat(count("retrieval_query")).isZero();
        assertThat(count("retrieval_hit")).isZero();
        assertThat(count("retrieval_decision")).isZero();
    }

    @Test
    void experimentServiceDoesNotDependOnProductionRetrievalFlowServices() {
        Set<String> fieldTypeNames = Arrays.stream(GeneralComplaintSimilarityExperimentService.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getName)
                .collect(Collectors.toSet());

        assertThat(fieldTypeNames).noneSatisfy(typeName -> assertThat(typeName).containsAnyOf(
                "RetrievalItemRepository",
                "RetrievalQueryRepository",
                "RetrievalHitRepository",
                "RetrievalDecisionRepository",
                "RetrievalQueryExecutionService",
                "RetrievalSearchService",
                "RetrievalEvidenceEvaluator"
        ));
    }

    private int count(String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        assertThat(count).isNotNull();
        return count;
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {

        @Bean
        @Primary
        CountingFakeEmbeddingPort countingFakeEmbeddingPort() {
            return new CountingFakeEmbeddingPort();
        }
    }

    static class CountingFakeEmbeddingPort implements EmbeddingPort {

        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public EmbeddingResult embed(String searchText) {
            callCount.incrementAndGet();
            if (searchText.contains("버스 정류장 A")) {
                return result(vector(1.0f, 0.0f));
            }
            if (searchText.contains("버스 정류장 B")) {
                return result(vector(0.9f, 0.1f));
            }
            return result(vector(0.0f, 1.0f));
        }

        int callCount() {
            return callCount.get();
        }

        void reset() {
            callCount.set(0);
        }

        private EmbeddingResult result(float[] vector) {
            return new EmbeddingResult(vector, "fake-embedding", 1536, "embed-v1-large-1536");
        }

        private float[] vector(float first, float second) {
            float[] vector = new float[1536];
            vector[0] = first;
            vector[1] = second;
            return vector;
        }
    }
}
