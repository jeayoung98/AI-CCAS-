package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.FactType;
import com.example.ccas.retrieval.domain.type.InputSource;
import com.example.ccas.retrieval.domain.type.OngoingStatus;
import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.example.ccas.retrieval.domain.type.TimePattern;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalHitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class RetrievalQueryTransactionRollbackIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_query_rollback_test")
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
    private RetrievalQueryExecutionService executionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM retrieval_hit");
        jdbcTemplate.update("DELETE FROM retrieval_query");
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");
    }

    @Test
    void rollsBackQueryWhenHitPersistenceFails() {
        assertThatThrownBy(() -> executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "분실물 조회 방법 문의",
                lostItemComplaint()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("intentional retrieval_hit persistence failure");

        assertThat(countRows("retrieval_query")).isZero();
        assertThat(countRows("retrieval_hit")).isZero();
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private StructuredComplaint lostItemComplaint() {
        return new StructuredComplaint(
                "공공장소 인근에서 지갑을 분실하여 찾는 방법을 문의하는 상황",
                new ComplaintContext(PlaceType.PUBLIC_FACILITY, RelationshipType.UNKNOWN, TimePattern.ONE_TIME),
                List.of(new ObservedFact(FactType.ACTION, "지갑 분실", "지갑을 잃어버렸다고 말함", Certainty.EXPLICIT)),
                List.of(),
                List.of(new SubjectMatter(SubjectMatterCode.LOST_ITEM, "유실물 문의", Certainty.EXPLICIT)),
                List.of(new RequestedAction(RequestedActionCode.SEARCH, "조회 방법 문의", Certainty.EXPLICIT)),
                OngoingStatus.PAST_EVENT,
                List.of()
        );
    }

    @TestConfiguration
    static class FailureConfig {

        @Bean
        @Primary
        EmbeddingPort fakeEmbeddingPort() {
            return searchText -> new EmbeddingResult(vector(1.0f, 0.0f), "text-embedding-3-large", 1536, "embed-v1-large-1536");
        }

        @Bean
        @Primary
        RetrievalHitRepository failingRetrievalHitRepository(NamedParameterJdbcTemplate jdbcTemplate) {
            return new RetrievalHitRepository(jdbcTemplate) {
                @Override
                public void saveAll(UUID queryId, com.example.ccas.retrieval.application.search.RetrievalCandidates candidates) {
                    throw new IllegalStateException("intentional retrieval_hit persistence failure");
                }
            };
        }

        private static float[] vector(float first, float second) {
            float[] vector = new float[1536];
            vector[0] = first;
            vector[1] = second;
            return vector;
        }
    }
}
