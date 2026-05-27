package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.application.ingestion.KnowledgeSourceIngestionService;
import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.DecisionReasonCode;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
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
        registry.add("retrieval.evaluation.threshold-profile-version", () -> "provisional-test-v1");
        registry.add("retrieval.evaluation.provisional", () -> "true");
        registry.add("retrieval.evaluation.case-min-similarity", () -> "0.80");
        registry.add("retrieval.evaluation.category-min-similarity", () -> "0.75");
        registry.add("retrieval.evaluation.guide-min-similarity", () -> "0.70");
        registry.add("retrieval.evaluation.category-margin-min", () -> "0.05");
        registry.add("retrieval.evaluation.case-agreement-min", () -> "0.67");
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
        jdbcTemplate.update("DELETE FROM retrieval_decision");
        jdbcTemplate.update("DELETE FROM retrieval_hit");
        jdbcTemplate.update("DELETE FROM retrieval_query");
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");
        fakeEmbeddingPort.reset();

        categorySourceId = createSource(KnowledgeSourceType.CATEGORY_POLICY, "category policy");
        guideSourceId = createSource(KnowledgeSourceType.OFFICIAL_DOCUMENT, "official guide");
        verifiedCaseSourceId = createSource(KnowledgeSourceType.TEAM_VERIFIED_CASESET, "verified cases");
    }

    @Test
    void storesTextQueryHitsAndDecisionForNonEmergencyLostItemComplaint() {
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-item-category", "lost item category",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"categoryName\":\"Lost item\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "lost-item-guide", "lost item guide",
                vector(1.0f, 0.0f), null, "{\"sectionTitle\":\"Lost item guide\"}");
        StructuredComplaint complaint = lostItemComplaint();

        RetrievalQueryExecutionResult result = executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "masked lost item question",
                complaint
        ));

        assertThat(result.queryId()).isNotNull();
        assertThat(result.riskSignalPresent()).isFalse();
        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.REFERENCE_SUPPORTED);
        assertThat(result.candidates().categoryReferences()).hasSize(1);
        assertThat(result.candidates().officialGuides()).hasSize(1);

        Map<String, Object> queryRow = queryRow(result.queryId());
        assertThat(queryRow.get("input_source")).isEqualTo("TEXT");
        assertThat(queryRow.get("masking_completed")).isEqualTo(true);
        assertThat(queryRow.get("masked_text")).isEqualTo("masked lost item question");
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
            assertThat(row.get("passed_channel_cutoff")).isEqualTo(true);
            assertThat(row.get("rejection_reason")).isNull();
        });

        Map<String, Object> decisionRow = decisionRow(result.queryId());
        assertThat(decisionRow.get("evidence_status")).isEqualTo("REFERENCE_SUPPORTED");
        assertThat(decisionRow.get("risk_signal_present")).isEqualTo(false);
        assertThat(decisionRow.get("best_category_score")).isNotNull();
        assertThat(decisionRow.get("best_guide_score")).isNotNull();
        assertThat(textArray(decisionRow, "decision_reason_codes"))
                .contains("NO_RELIABLE_VERIFIED_CASE", "RELIABLE_CATEGORY_REFERENCE_FOUND", "RELIABLE_OFFICIAL_GUIDE_FOUND");
        assertThat(decisionRow.get("threshold_profile_version")).isEqualTo("provisional-test-v1");
        assertThat(fakeEmbeddingPort.callCount()).isEqualTo(1);
        assertThat(fakeEmbeddingPort.wasCalledInsideTransaction()).isFalse();
    }

    @Test
    void storesCaseAndReferenceSupportedDecision() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "lost-case", "lost item case",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"factualSummary\":\"Lost wallet\"}");
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-category", "lost item category",
                vector(0.9f, 0.43589f), "LOST_ITEM", "{\"categoryName\":\"Lost item\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "lost-guide", "lost item guide",
                vector(1.0f, 0.0f), null, "{\"sectionTitle\":\"Lost item guide\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.CASE_AND_REFERENCE_SUPPORTED);
        assertThat(result.decision().bestCaseScore()).isNotNull();
        assertThat(result.decision().bestCategoryScore()).isNotNull();
        assertThat(result.decision().bestGuideScore()).isNotNull();
        assertThat(countRows("retrieval_decision")).isEqualTo(1);
        assertThat(hitRows(result.queryId())).allSatisfy(row -> assertThat(row.get("passed_channel_cutoff")).isEqualTo(true));
    }

    @Test
    void storesGuideOnlyDecision() {
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "lost-guide", "lost item guide",
                vector(1.0f, 0.0f), null, "{\"sectionTitle\":\"Lost item guide\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.GUIDE_ONLY);
        assertThat(decisionRow(result.queryId()).get("evidence_status")).isEqualTo("GUIDE_ONLY");
    }

    @Test
    void storesInsufficientDecisionForEmptyResults() {
        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.candidates().verifiedCases()).isEmpty();
        assertThat(result.candidates().categoryReferences()).isEmpty();
        assertThat(result.candidates().officialGuides()).isEmpty();
        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(countRows("retrieval_query")).isEqualTo(1);
        assertThat(countRows("retrieval_hit")).isZero();
        assertThat(countRows("retrieval_decision")).isEqualTo(1);
    }

    @Test
    void storesInsufficientDecisionAndRejectedHitsWhenAllScoresAreBelowCutoff() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "low-case", "low case",
                vector(0.6f, 0.8f), "LOST_ITEM", "{\"factualSummary\":\"Low case\"}");
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "low-category", "low category",
                vector(0.6f, 0.8f), "LOST_ITEM", "{\"categoryName\":\"Low category\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "low-guide", "low guide",
                vector(0.6f, 0.8f), null, "{\"sectionTitle\":\"Low guide\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(hitRows(result.queryId())).allSatisfy(row -> assertThat(row.get("passed_channel_cutoff")).isEqualTo(false));
        assertThat(hitRows(result.queryId())).extracting(row -> row.get("rejection_reason"))
                .contains("BELOW_CASE_MIN_SIMILARITY", "BELOW_CATEGORY_MIN_SIMILARITY", "BELOW_GUIDE_MIN_SIMILARITY");
    }

    @Test
    void storesAmbiguousDecisionForCategoryMarginConflict() {
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-category", "lost category",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"categoryName\":\"Lost item\"}");
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "cyber-category", "cyber category",
                vector(0.98f, 0.198997f), "CYBER_TRANSACTION", "{\"categoryName\":\"Cyber transaction\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "lost-guide", "lost guide",
                vector(1.0f, 0.0f), null, "{\"sectionTitle\":\"Lost guide\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(result.decision().decisionReasonCodes()).contains(DecisionReasonCode.CATEGORY_MARGIN_TOO_SMALL);
        assertThat(decisionRow(result.queryId()).get("category_margin")).isNotNull();
    }

    @Test
    void storesAmbiguousDecisionForCaseCategoryConflict() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "case-1", "case 1",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"factualSummary\":\"Lost case 1\"}");
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "case-2", "case 2",
                vector(0.99f, 0.141067f), "LOST_ITEM", "{\"factualSummary\":\"Lost case 2\"}");
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "case-3", "case 3",
                vector(0.98f, 0.198997f), "CYBER_TRANSACTION", "{\"factualSummary\":\"Cyber case\"}");
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "case-4", "case 4",
                vector(0.97f, 0.243105f), "SAFETY_THREAT", "{\"factualSummary\":\"Safety case\"}");
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-category", "lost category",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"categoryName\":\"Lost item\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "lost-guide", "lost guide",
                vector(1.0f, 0.0f), null, "{\"sectionTitle\":\"Lost guide\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(result.decision().decisionReasonCodes()).contains(DecisionReasonCode.CASE_CATEGORY_CONFLICT);
        assertThat(decisionRow(result.queryId()).get("case_category_agreement")).isNotNull();
    }

    @Test
    void storesAmbiguousDecisionForCaseReferenceMismatch() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "lost-case", "lost case",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"factualSummary\":\"Lost wallet\"}");
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "cyber-category", "cyber category",
                vector(1.0f, 0.0f), "CYBER_TRANSACTION", "{\"categoryName\":\"Cyber transaction\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, guideSourceId, "guide", "guide",
                vector(1.0f, 0.0f), null, "{\"sectionTitle\":\"Guide\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(result.decision().decisionReasonCodes()).contains(DecisionReasonCode.CASE_REFERENCE_CATEGORY_MISMATCH);
    }

    @Test
    void treatsCaseAndCategoryWithoutOfficialGuideAsInsufficient() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "lost-case", "lost case",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"factualSummary\":\"Lost wallet\"}");
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-category", "lost category",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"categoryName\":\"Lost item\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(result.decision().decisionReasonCodes()).contains(DecisionReasonCode.NO_RELIABLE_OFFICIAL_GUIDE);
    }

    @Test
    void preservesRiskSignalInDecisionWithoutDecidingEmergencyRoute() {
        RetrievalQueryExecutionResult result = executionService.execute(command(safetyThreatComplaint()));

        assertThat(result.riskSignalPresent()).isTrue();
        assertThat(result.decision().riskSignalPresent()).isTrue();
        assertThat(result.decision().evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(result.decision().decisionReasonCodes()).contains(DecisionReasonCode.RISK_SIGNAL_PRESENT);
        assertThat(textArray(queryRow(result.queryId()), "risk_signal_codes")).containsExactly("DEATH_THREAT");
        assertThat(decisionRow(result.queryId()).get("risk_signal_present")).isEqualTo(true);
    }

    @Test
    void generatesInternalQueryIdAndUsesItForHitsAndDecision() {
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categorySourceId, "lost-category", "lost category",
                vector(1.0f, 0.0f), "LOST_ITEM", "{\"categoryName\":\"Lost item\"}");

        RetrievalQueryExecutionResult result = executionService.execute(command(lostItemComplaint()));

        assertThat(result.queryId()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_query WHERE id = ?", Integer.class, result.queryId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_hit WHERE query_id = ?", Integer.class, result.queryId()))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM retrieval_decision WHERE query_id = ?", Integer.class, result.queryId()))
                .isEqualTo(1);
    }

    @Test
    void rejectsMaskingIncompleteBeforeEmbeddingAndPersistence() {
        assertThatThrownBy(() -> executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                false,
                "masked text",
                lostItemComplaint()
        ))).isInstanceOf(InvalidRetrievalQueryException.class);

        assertThat(fakeEmbeddingPort.callCount()).isZero();
        assertThat(countRows("retrieval_query")).isZero();
        assertThat(countRows("retrieval_hit")).isZero();
        assertThat(countRows("retrieval_decision")).isZero();
    }

    @Test
    void rejectsInvalidStructuredComplaintBeforeEmbeddingAndPersistence() {
        StructuredComplaint invalidComplaint = new StructuredComplaint(
                " ",
                new ComplaintContext(PlaceType.PUBLIC_FACILITY, RelationshipType.UNKNOWN, TimePattern.ONE_TIME),
                List.of(new ObservedFact(FactType.ACTION, "wallet lost", "wallet lost", Certainty.EXPLICIT)),
                List.of(),
                List.of(),
                List.of(new RequestedAction(RequestedActionCode.SEARCH, "search request", Certainty.EXPLICIT)),
                OngoingStatus.PAST_EVENT,
                List.of()
        );

        assertThatThrownBy(() -> executionService.execute(new ExecuteRetrievalQueryCommand(
                InputSource.TEXT,
                true,
                "masked text",
                invalidComplaint
        ))).isInstanceOf(InvalidRetrievalQueryException.class);

        assertThat(fakeEmbeddingPort.callCount()).isZero();
        assertThat(countRows("retrieval_query")).isZero();
        assertThat(countRows("retrieval_hit")).isZero();
        assertThat(countRows("retrieval_decision")).isZero();
    }

    private ExecuteRetrievalQueryCommand command(StructuredComplaint complaint) {
        return new ExecuteRetrievalQueryCommand(InputSource.TEXT, true, "masked text", complaint);
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
            String categoryCode,
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
                subjectMatterCodes(itemType, categoryCode),
                requestedActionCodes(itemType),
                List.of(),
                null,
                null,
                null,
                categoryCode,
                null,
                ReviewStatus.VERIFIED,
                true,
                new EmbeddingResult(vector, "text-embedding-3-large", 1536, "embed-v1-large-1536"),
                "complaint-structure-v1",
                "test-search-text-version"
        ));
    }

    private List<String> subjectMatterCodes(RetrievalItemType itemType, String categoryCode) {
        if ("CYBER_TRANSACTION".equals(categoryCode)) {
            return List.of("CYBER_TRANSACTION");
        }
        if ("SAFETY_THREAT".equals(categoryCode)) {
            return List.of("SAFETY_THREAT");
        }
        if (itemType == RetrievalItemType.OFFICIAL_GUIDE) {
            return List.of("LOST_ITEM");
        }
        return List.of("LOST_ITEM");
    }

    private List<String> requestedActionCodes(RetrievalItemType itemType) {
        if (itemType == RetrievalItemType.VERIFIED_CASE) {
            return List.of("REPORT");
        }
        return List.of("SEARCH");
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

    private Map<String, Object> decisionRow(UUID queryId) {
        return jdbcTemplate.queryForMap("""
                SELECT
                    evidence_status,
                    risk_signal_present,
                    best_case_score,
                    best_category_score,
                    best_guide_score,
                    category_margin,
                    case_category_agreement,
                    array_to_json(decision_reason_codes)::text AS decision_reason_codes,
                    threshold_profile_version
                FROM retrieval_decision
                WHERE query_id = ?
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
                "User lost a wallet near a public place and asks how to find it.",
                new ComplaintContext(PlaceType.PUBLIC_FACILITY, RelationshipType.UNKNOWN, TimePattern.ONE_TIME),
                List.of(new ObservedFact(FactType.ACTION, "wallet lost", "user said wallet was lost", Certainty.EXPLICIT)),
                List.of(),
                List.of(new SubjectMatter(SubjectMatterCode.LOST_ITEM, "lost item inquiry", Certainty.EXPLICIT)),
                List.of(new RequestedAction(RequestedActionCode.SEARCH, "asks how to search", Certainty.EXPLICIT)),
                OngoingStatus.PAST_EVENT,
                List.of()
        );
    }

    private StructuredComplaint safetyThreatComplaint() {
        return new StructuredComplaint(
                "Neighbor repeatedly visited and made a death threat.",
                new ComplaintContext(PlaceType.RESIDENCE, RelationshipType.NEIGHBOR, TimePattern.REPEATED),
                List.of(new ObservedFact(FactType.HARM, "death threat", "user said the neighbor threatened to kill", Certainty.EXPLICIT)),
                List.of(new RiskSignal(RiskSignalCode.DEATH_THREAT, "death threat stated", Certainty.EXPLICIT)),
                List.of(new SubjectMatter(SubjectMatterCode.SAFETY_THREAT, "safety threat", Certainty.EXPLICIT)),
                List.of(new RequestedAction(RequestedActionCode.REPORT, "report request", Certainty.EXPLICIT)),
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
