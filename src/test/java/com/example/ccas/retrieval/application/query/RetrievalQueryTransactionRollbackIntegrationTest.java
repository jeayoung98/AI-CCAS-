package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceDecision;
import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.application.ingestion.KnowledgeSourceIngestionService;
import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.FactType;
import com.example.ccas.retrieval.domain.type.InputSource;
import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.OngoingStatus;
import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.example.ccas.retrieval.domain.type.TimePattern;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalDecisionRepository;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalItemInsertRow;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalItemRepository;
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
    private KnowledgeSourceIngestionService sourceIngestionService;

    @Autowired
    private RetrievalItemRepository itemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long categorySourceId;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM retrieval_decision");
        jdbcTemplate.update("DELETE FROM retrieval_hit");
        jdbcTemplate.update("DELETE FROM retrieval_query");
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");

        categorySourceId = sourceIngestionService.createSource(new CreateKnowledgeSourceCommand(
                KnowledgeSourceType.CATEGORY_POLICY,
                "category policy",
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void rollsBackQueryAndHitsWhenDecisionPersistenceFails() {
        itemRepository.save(new RetrievalItemInsertRow(
                RetrievalItemType.CATEGORY_REFERENCE,
                categorySourceId,
                "lost-category",
                "lost category",
                null,
                "{\"categoryName\":\"Lost item\"}",
                "lost item category",
                List.of("LOST_ITEM"),
                List.of("SEARCH"),
                List.of(),
                null,
                null,
                null,
                "LOST_ITEM",
                null,
                ReviewStatus.VERIFIED,
                true,
                new EmbeddingResult(vector(1.0f, 0.0f), "text-embedding-3-large", 1536, "embed-v1-large-1536"),
                "complaint-structure-v1",
                "test-search-text-version"
        ));

        assertThatThrownBy(() -> executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "masked text",
                lostItemComplaint()
        ))).isInstanceOf(IllegalStateException.class)
                .hasMessage("intentional retrieval_decision persistence failure");

        assertThat(countRows("retrieval_query")).isZero();
        assertThat(countRows("retrieval_hit")).isZero();
        assertThat(countRows("retrieval_decision")).isZero();
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private StructuredComplaint lostItemComplaint() {
        return new StructuredComplaint(
                "User lost a wallet near a public place and asks how to find it.",
                new ComplaintContext(PlaceType.PUBLIC_FACILITY, RelationshipType.UNKNOWN, TimePattern.ONE_TIME),
                List.of(new ObservedFact(FactType.ACTION, "wallet lost", "user said wallet was lost", Certainty.EXPLICIT)),
                List.of(),
                List.of(new SubjectMatter(SubjectMatterCode.LOST_ITEM, "lost item inquiry", Certainty.EXPLICIT)),
                List.of(new RequestedAction(RequestedActionCode.SEARCH, "search request", Certainty.EXPLICIT)),
                OngoingStatus.PAST_EVENT,
                List.of()
        );
    }

    private static float[] vector(float first, float second) {
        float[] vector = new float[1536];
        vector[0] = first;
        vector[1] = second;
        return vector;
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
        RetrievalDecisionRepository failingRetrievalDecisionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
            return new RetrievalDecisionRepository(jdbcTemplate) {
                @Override
                public void save(UUID queryId, RetrievalEvidenceDecision decision) {
                    throw new IllegalStateException("intentional retrieval_decision persistence failure");
                }
            };
        }
    }
}
