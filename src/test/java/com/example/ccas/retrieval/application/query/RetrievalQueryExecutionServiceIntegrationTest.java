package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.application.ingestion.KnowledgeSourceIngestionService;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
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
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.example.ccas.retrieval.domain.type.TimePattern;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class RetrievalQueryExecutionServiceIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_query_execution_test")
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
    private SearchTextBuilder searchTextBuilder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeEmbeddingPort fakeEmbeddingPort;

    private long categorySourceId;
    private long guideSourceId;
    private long verifiedCaseSourceId;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM retrieval_hit");
        jdbcTemplate.update("DELETE FROM retrieval_query");
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");
        fakeEmbeddingPort.reset();

        categorySourceId = createSource(KnowledgeSourceType.CATEGORY_POLICY, "유형 기준");
        guideSourceId = createSource(KnowledgeSourceType.OFFICIAL_DOCUMENT, "공식 안내");
        verifiedCaseSourceId = createSource(KnowledgeSourceType.TEAM_VERIFIED_CASESET, "검수 사례집");
    }

    @Test
    void storesTextQueryAndRetrievalHitsForNonEmergencyLostItemComplaint() {
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-item-category", "유실물 기준",
                vector(1.0f, 0.0f), "{\"categoryName\":\"유실물 관련 민원\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "lost-item-guide", "유실물 안내",
                vector(1.0f, 0.0f), "{\"sectionTitle\":\"유실물 민원 안내\"}");
        StructuredComplaint complaint = lostItemComplaint();

        RetrievalQueryExecutionResult result = executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "지하철역 근처에서 지갑을 잃어버렸는데 찾는 방법을 문의함",
                complaint
        ));

        assertThat(result.queryId()).isNotNull();
        assertThat(result.riskSignalPresent()).isFalse();
        assertThat(result.candidates().categoryReferences()).hasSize(1);
        assertThat(result.candidates().officialGuides()).hasSize(1);

        Map<String, Object> queryRow = queryRow(result.queryId());
        assertThat(queryRow.get("input_source")).isEqualTo("TEXT");
        assertThat(queryRow.get("masking_completed")).isEqualTo(true);
        assertThat(queryRow.get("masked_text")).isEqualTo("지하철역 근처에서 지갑을 잃어버렸는데 찾는 방법을 문의함");
        assertThat(queryRow.get("structured_payload_text").toString()).contains("\"factualSummary\"");
        assertThat(queryRow.get("search_text")).isEqualTo(searchTextBuilder.buildForComplaint(complaint));
        assertThat(textArray(queryRow, "subject_matter_codes")).containsExactly("LOST_ITEM");
        assertThat(textArray(queryRow, "requested_action_codes")).containsExactly("SEARCH");
        assertThat(textArray(queryRow, "risk_signal_codes")).isEmpty();
        assertThat(queryRow.get("embedding_present")).isEqualTo(true);
        assertThat(queryRow.get("embedding_model")).isEqualTo("text-embedding-3-large");
        assertThat(queryRow.get("embedding_dimensions")).isEqualTo(1536);
        assertThat(queryRow.get("structure_version")).isEqualTo("complaint-structure-v1");
        assertThat(queryRow.get("search_text_version")).isEqualTo("complaint-search-text-v1");
        assertThat(queryRow.get("embedding_version")).isEqualTo("embed-v1-large-1536");

        List<Map<String, Object>> hits = hitRows(result.queryId());
        assertThat(hits).hasSize(2);
        assertThat(hits).extracting(row -> row.get("channel")).containsExactly("CATEGORY_REFERENCE", "OFFICIAL_GUIDE");
        assertThat(hits).allSatisfy(row -> {
            assertThat(row.get("passed_channel_cutoff")).isNull();
            assertThat(row.get("rejection_reason")).isNull();
        });
        assertThat(fakeEmbeddingPort.callCount()).isEqualTo(1);
        assertThat(fakeEmbeddingPort.wasCalledInsideTransaction()).isFalse();
    }

    @Test
    void preservesRiskSignalCodesWithoutMakingDecision() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "safety-case", "안전 위협 사례",
                vector(1.0f, 0.0f), "{\"factualSummary\":\"안전 위협 사례\"}");
        StructuredComplaint complaint = safetyThreatComplaint();

        RetrievalQueryExecutionResult result = executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "이웃이 반복적으로 찾아와 죽이겠다고 말함",
                complaint
        ));

        assertThat(result.riskSignalPresent()).isTrue();
        assertThat(textArray(queryRow(result.queryId()), "risk_signal_codes")).containsExactly("DEATH_THREAT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_decision", Integer.class)).isZero();
    }

    @Test
    void generatesInternalQueryIdAndUsesItForHits() {
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-item-category", "유실물 기준",
                vector(1.0f, 0.0f), "{\"categoryName\":\"유실물 관련 민원\"}");

        RetrievalQueryExecutionResult result = executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "분실물 조회 방법 문의",
                lostItemComplaint()
        ));

        assertThat(result.queryId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_query WHERE id = ?", Integer.class, result.queryId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_hit WHERE query_id = ?", Integer.class, result.queryId()))
                .isEqualTo(1);
    }

    @Test
    void rejectsMaskingIncompleteBeforeEmbeddingAndPersistence() {
        assertThatThrownBy(() -> executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                false,
                "마스킹 텍스트",
                lostItemComplaint()
        ))).isInstanceOf(InvalidRetrievalQueryException.class);

        assertThat(fakeEmbeddingPort.callCount()).isZero();
        assertThat(countRows("retrieval_query")).isZero();
        assertThat(countRows("retrieval_hit")).isZero();
    }

    @Test
    void rejectsInvalidStructuredComplaintBeforeEmbeddingAndPersistence() {
        StructuredComplaint invalidComplaint = new StructuredComplaint(
                " ",
                new ComplaintContext(PlaceType.PUBLIC_FACILITY, RelationshipType.UNKNOWN, TimePattern.ONE_TIME),
                List.of(new ObservedFact(FactType.ACTION, "지갑 분실", "지갑 분실", Certainty.EXPLICIT)),
                List.of(),
                List.of(),
                List.of(new RequestedAction(RequestedActionCode.SEARCH, "조회 문의", Certainty.EXPLICIT)),
                OngoingStatus.PAST_EVENT,
                List.of()
        );

        assertThatThrownBy(() -> executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "마스킹 텍스트",
                invalidComplaint
        ))).isInstanceOf(InvalidRetrievalQueryException.class);

        assertThat(fakeEmbeddingPort.callCount()).isZero();
        assertThat(countRows("retrieval_query")).isZero();
        assertThat(countRows("retrieval_hit")).isZero();
    }

    @Test
    void storesQueryAndReturnsEmptyCandidatesWhenNoSearchResultsExist() {
        RetrievalQueryExecutionResult result = executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "분실물 조회 방법 문의",
                lostItemComplaint()
        ));

        assertThat(result.candidates().verifiedCases()).isEmpty();
        assertThat(result.candidates().categoryReferences()).isEmpty();
        assertThat(result.candidates().officialGuides()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_query WHERE id = ?", Integer.class, result.queryId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_hit WHERE query_id = ?", Integer.class, result.queryId()))
                .isZero();
    }

    private long createSource(KnowledgeSourceType sourceType, String title) {
        return sourceIngestionService.createSource(new CreateKnowledgeSourceCommand(
                sourceType,
                title,
                null,
                null,
                null,
                null
        ));
    }

    private long saveItem(
            RetrievalItemType itemType,
            long sourceId,
            String externalKey,
            String title,
            float[] vector,
            String structuredPayloadJson
    ) {
        return itemRepository.save(new RetrievalItemInsertRow(
                itemType,
                sourceId,
                externalKey,
                title,
                null,
                structuredPayloadJson,
                "search text " + externalKey,
                subjectMatterCodes(itemType),
                requestedActionCodes(itemType),
                List.of(),
                null,
                null,
                null,
                itemType == RetrievalItemType.CATEGORY_REFERENCE ? "LOST_ITEM_REFERENCE" : null,
                null,
                ReviewStatus.VERIFIED,
                true,
                new EmbeddingResult(vector, "text-embedding-3-large", 1536, "embed-v1-large-1536"),
                "complaint-structure-v1",
                "test-search-text-version"
        ));
    }

    private List<String> subjectMatterCodes(RetrievalItemType itemType) {
        if (itemType == RetrievalItemType.CATEGORY_REFERENCE || itemType == RetrievalItemType.OFFICIAL_GUIDE) {
            return List.of("LOST_ITEM");
        }
        return List.of("SAFETY_THREAT");
    }

    private List<String> requestedActionCodes(RetrievalItemType itemType) {
        if (itemType == RetrievalItemType.CATEGORY_REFERENCE || itemType == RetrievalItemType.OFFICIAL_GUIDE) {
            return List.of("SEARCH");
        }
        return List.of("REPORT");
    }

    private Map<String, Object> queryRow(UUID queryId) {
        return jdbcTemplate.queryForMap("""
                SELECT
                    input_source,
                    masking_completed,
                    masked_text,
                    structured_payload::text AS structured_payload_text,
                    search_text,
                    array_to_json(subject_matter_codes)::text AS subject_matter_codes,
                    array_to_json(requested_action_codes)::text AS requested_action_codes,
                    array_to_json(risk_signal_codes)::text AS risk_signal_codes,
                    embedding IS NOT NULL AS embedding_present,
                    embedding_model,
                    embedding_dimensions,
                    structure_version,
                    search_text_version,
                    embedding_version
                FROM retrieval_query
                WHERE id = ?
                """, queryId);
    }

    private List<Map<String, Object>> hitRows(UUID queryId) {
        return jdbcTemplate.queryForList("""
                SELECT
                    channel,
                    result_rank,
                    passed_channel_cutoff,
                    rejection_reason
                FROM retrieval_hit
                WHERE query_id = ?
                ORDER BY channel, result_rank
                """, queryId);
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private List<String> textArray(Map<String, Object> row, String columnName) {
        String json = row.get(columnName).toString();
        if ("[]".equals(json)) {
            return List.of();
        }
        return java.util.Arrays.stream(json.substring(1, json.length() - 1).split(","))
                .map(value -> value.substring(1, value.length() - 1))
                .toList();
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

    private StructuredComplaint safetyThreatComplaint() {
        return new StructuredComplaint(
                "이웃 주민이 반복적으로 찾아오고 죽이겠다고 말한 상황",
                new ComplaintContext(PlaceType.RESIDENCE, RelationshipType.NEIGHBOR, TimePattern.REPEATED),
                List.of(new ObservedFact(FactType.HARM, "죽이겠다는 발언", "죽이겠다고 말함", Certainty.EXPLICIT)),
                List.of(new RiskSignal(RiskSignalCode.DEATH_THREAT, "죽이겠다고 말함", Certainty.EXPLICIT)),
                List.of(new SubjectMatter(SubjectMatterCode.SAFETY_THREAT, "안전 위협", Certainty.EXPLICIT)),
                List.of(new RequestedAction(RequestedActionCode.REPORT, "신고 문의", Certainty.EXPLICIT)),
                OngoingStatus.REPEATED_AND_MAY_CONTINUE,
                List.of()
        );
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[1536];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    @TestConfiguration
    static class FakeEmbeddingConfig {

        @Bean
        @Primary
        FakeEmbeddingPort fakeEmbeddingPort() {
            return new FakeEmbeddingPort();
        }
    }

    static class FakeEmbeddingPort implements EmbeddingPort {

        private final AtomicInteger callCount = new AtomicInteger();
        private final AtomicBoolean calledInsideTransaction = new AtomicBoolean();

        @Override
        public EmbeddingResult embed(String searchText) {
            callCount.incrementAndGet();
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                calledInsideTransaction.set(true);
            }
            return new EmbeddingResult(vector(1.0f, 0.0f), "text-embedding-3-large", 1536, "embed-v1-large-1536");
        }

        void reset() {
            callCount.set(0);
            calledInsideTransaction.set(false);
        }

        int callCount() {
            return callCount.get();
        }

        boolean wasCalledInsideTransaction() {
            return calledInsideTransaction.get();
        }

        private float[] vector(float first, float second) {
            float[] vector = new float[1536];
            vector[0] = first;
            vector[1] = second;
            return vector;
        }
    }
}
