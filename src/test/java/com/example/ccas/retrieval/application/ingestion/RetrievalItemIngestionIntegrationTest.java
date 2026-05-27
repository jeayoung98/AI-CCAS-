package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.application.text.SearchTextBuilder;
import com.example.ccas.retrieval.domain.CategoryReference;
import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.OfficialGuideChunk;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.FactType;
import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.OngoingStatus;
import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.example.ccas.retrieval.domain.type.TimePattern;
import com.example.ccas.retrieval.embedding.EmbeddingPort;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class RetrievalItemIngestionIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_ingestion_test")
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
    private KnowledgeSourceIngestionService knowledgeSourceIngestionService;

    @Autowired
    private RetrievalItemIngestionService retrievalItemIngestionService;

    @Autowired
    private SearchTextBuilder searchTextBuilder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FakeEmbeddingPort fakeEmbeddingPort;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");
        fakeEmbeddingPort.reset();
    }

    @Test
    void savesKnowledgeSources() {
        long officialDocumentId = createSource(KnowledgeSourceType.OFFICIAL_DOCUMENT, "공식 문서");
        long verifiedCasesId = createSource(KnowledgeSourceType.TEAM_VERIFIED_CASESET, "검수 사례집");
        long categoryPolicyId = createSource(KnowledgeSourceType.CATEGORY_POLICY, "유형 기준");

        assertThat(sourceTypeOf(officialDocumentId)).isEqualTo("OFFICIAL_DOCUMENT");
        assertThat(sourceTypeOf(verifiedCasesId)).isEqualTo("TEAM_VERIFIED_CASESET");
        assertThat(sourceTypeOf(categoryPolicyId)).isEqualTo("CATEGORY_POLICY");
    }

    @Test
    void ingestsVerifiedCase() {
        long sourceId = createSource(KnowledgeSourceType.TEAM_VERIFIED_CASESET, "검수 사례집");
        StructuredComplaint complaint = safetyThreatComplaint();

        long itemId = retrievalItemIngestionService.ingestVerifiedCase(new IngestVerifiedCaseCommand(
                sourceId,
                "verified-case-1",
                "반복 협박 검수 사례",
                "이웃이 반복적으로 찾아와 협박한 사례",
                complaint,
                "SAFETY_THREAT",
                "ROUTE_TEST",
                ReviewStatus.VERIFIED
        ));

        Map<String, Object> row = retrievalItemRow(itemId);
        assertThat(row.get("item_type")).isEqualTo("VERIFIED_CASE");
        assertThat(row.get("masked_text")).isEqualTo("이웃이 반복적으로 찾아와 협박한 사례");
        assertThat(row.get("structured_payload_text").toString()).contains("\"factualSummary\"");
        assertThat(row.get("search_text")).isEqualTo(searchTextBuilder.buildForComplaint(complaint));
        assertThat(textArray(row, "subject_matter_codes")).containsExactly("SAFETY_THREAT");
        assertThat(textArray(row, "requested_action_codes")).containsExactly("REPORT");
        assertThat(textArray(row, "risk_signal_codes")).containsExactly("DEATH_THREAT", "STALKING_PATTERN");
        assertThat(row.get("verified_category_code")).isEqualTo("SAFETY_THREAT");
        assertThat(row.get("embedding_model")).isEqualTo("text-embedding-3-large");
        assertThat(row.get("embedding_dimensions")).isEqualTo(1536);
        assertThat(row.get("embedding_version")).isEqualTo("embed-v1-large-1536");
        assertThat(row.get("search_text_version")).isEqualTo("complaint-search-text-v1");
        assertThat(row.get("embedding_present")).isEqualTo(true);
        assertThat(row.get("embedding_vector_dimensions")).isEqualTo(1536);
    }

    @Test
    void rejectsVerifiedCaseWithBlankCategoryWhenReviewStatusIsVerified() {
        long sourceId = createSource(KnowledgeSourceType.TEAM_VERIFIED_CASESET, "검수 사례집");

        assertThatThrownBy(() -> retrievalItemIngestionService.ingestVerifiedCase(new IngestVerifiedCaseCommand(
                sourceId,
                "verified-case-invalid",
                "검수 사례",
                "마스킹 텍스트",
                safetyThreatComplaint(),
                " ",
                null,
                ReviewStatus.VERIFIED
        ))).isInstanceOf(IllegalArgumentException.class);

        assertThat(fakeEmbeddingPort.callCount()).isZero();
    }

    @Test
    void ingestsCategoryReference() {
        long sourceId = createSource(KnowledgeSourceType.CATEGORY_POLICY, "유형 기준");
        CategoryReference reference = lostItemReference();

        long itemId = retrievalItemIngestionService.ingestCategoryReference(new IngestCategoryReferenceCommand(
                sourceId,
                "lost-item-reference",
                "유실물 기준",
                reference,
                ReviewStatus.VERIFIED
        ));

        Map<String, Object> row = retrievalItemRow(itemId);
        assertThat(row.get("item_type")).isEqualTo("CATEGORY_REFERENCE");
        assertThat(row.get("masked_text")).isNull();
        assertThat(row.get("structured_payload_text").toString()).contains("\"categoryCode\": \"LOST_ITEM_REFERENCE\"");
        assertThat(row.get("search_text")).isEqualTo(searchTextBuilder.buildForCategoryReference(reference));
        assertThat(row.get("verified_category_code")).isEqualTo("LOST_ITEM_REFERENCE");
        assertThat(row.get("verified_route_code")).isNull();
        assertThat(textArray(row, "subject_matter_codes")).containsExactly("LOST_ITEM");
        assertThat(textArray(row, "requested_action_codes")).containsExactly("SEARCH", "CONSULT");
        assertThat(row.get("search_text_version")).isEqualTo("category-reference-search-text-v1");
        assertThat(row.get("embedding_present")).isEqualTo(true);
        assertThat(row.get("embedding_vector_dimensions")).isEqualTo(1536);
    }

    @Test
    void ingestsOfficialGuide() {
        long sourceId = createSource(KnowledgeSourceType.OFFICIAL_DOCUMENT, "공식 문서");
        OfficialGuideChunk guideChunk = lostItemGuideChunk();

        long itemId = retrievalItemIngestionService.ingestOfficialGuide(new IngestOfficialGuideCommand(
                sourceId,
                "lost-item-guide",
                "유실물 공식 안내",
                guideChunk,
                ReviewStatus.VERIFIED
        ));

        Map<String, Object> row = retrievalItemRow(itemId);
        assertThat(row.get("item_type")).isEqualTo("OFFICIAL_GUIDE");
        assertThat(row.get("masked_text")).isNull();
        assertThat(row.get("structured_payload_text").toString()).contains("\"documentTitle\": \"경찰 민원 안내\"");
        assertThat(row.get("search_text")).isEqualTo(searchTextBuilder.buildForOfficialGuide(guideChunk));
        assertThat(row.get("verified_category_code")).isNull();
        assertThat(row.get("verified_route_code")).isNull();
        assertThat(textArray(row, "subject_matter_codes")).containsExactly("LOST_ITEM");
        assertThat(textArray(row, "requested_action_codes")).containsExactly("SEARCH", "REPORT");
        assertThat(row.get("search_text_version")).isEqualTo("official-guide-search-text-v1");
        assertThat(row.get("embedding_present")).isEqualTo(true);
        assertThat(row.get("embedding_vector_dimensions")).isEqualTo(1536);
    }

    @Test
    void rejectsInvalidSourceTypesBeforeEmbedding() {
        long officialDocumentId = createSource(KnowledgeSourceType.OFFICIAL_DOCUMENT, "공식 문서");
        long verifiedCasesId = createSource(KnowledgeSourceType.TEAM_VERIFIED_CASESET, "검수 사례집");
        long categoryPolicyId = createSource(KnowledgeSourceType.CATEGORY_POLICY, "유형 기준");

        assertThatThrownBy(() -> retrievalItemIngestionService.ingestVerifiedCase(new IngestVerifiedCaseCommand(
                officialDocumentId, "bad-verified-case", "검수 사례", "마스킹", safetyThreatComplaint(),
                "SAFETY_THREAT", null, ReviewStatus.VERIFIED
        ))).isInstanceOf(InvalidKnowledgeSourceTypeException.class);

        assertThatThrownBy(() -> retrievalItemIngestionService.ingestCategoryReference(new IngestCategoryReferenceCommand(
                verifiedCasesId, "bad-category", "유형 기준", lostItemReference(), ReviewStatus.VERIFIED
        ))).isInstanceOf(InvalidKnowledgeSourceTypeException.class);

        assertThatThrownBy(() -> retrievalItemIngestionService.ingestOfficialGuide(new IngestOfficialGuideCommand(
                categoryPolicyId, "bad-guide", "공식 안내", lostItemGuideChunk(), ReviewStatus.VERIFIED
        ))).isInstanceOf(InvalidKnowledgeSourceTypeException.class);

        assertThat(fakeEmbeddingPort.callCount()).isZero();
    }

    @Test
    void rejectsMissingSourceBeforeEmbedding() {
        assertThatThrownBy(() -> retrievalItemIngestionService.ingestCategoryReference(new IngestCategoryReferenceCommand(
                999_999L,
                "missing-source",
                "유형 기준",
                lostItemReference(),
                ReviewStatus.VERIFIED
        ))).isInstanceOf(SourceNotFoundException.class);

        assertThat(fakeEmbeddingPort.callCount()).isZero();
    }

    @Test
    void rejectsDuplicateExternalKeyItemTypeAndEmbeddingVersion() {
        long sourceId = createSource(KnowledgeSourceType.CATEGORY_POLICY, "유형 기준");
        IngestCategoryReferenceCommand command = new IngestCategoryReferenceCommand(
                sourceId,
                "duplicate-key",
                "유실물 기준",
                lostItemReference(),
                ReviewStatus.VERIFIED
        );

        retrievalItemIngestionService.ingestCategoryReference(command);

        assertThatThrownBy(() -> retrievalItemIngestionService.ingestCategoryReference(command))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long createSource(KnowledgeSourceType sourceType, String title) {
        return knowledgeSourceIngestionService.createSource(new CreateKnowledgeSourceCommand(
                sourceType,
                title,
                null,
                null,
                null,
                null
        ));
    }

    private String sourceTypeOf(long sourceId) {
        return jdbcTemplate.queryForObject(
                "SELECT source_type FROM knowledge_source WHERE id = ?",
                String.class,
                sourceId
        );
    }

    private Map<String, Object> retrievalItemRow(long itemId) {
        return jdbcTemplate.queryForMap("""
                SELECT
                    item_type,
                    masked_text,
                    structured_payload::text AS structured_payload_text,
                    search_text,
                    array_to_json(subject_matter_codes)::text AS subject_matter_codes,
                    array_to_json(requested_action_codes)::text AS requested_action_codes,
                    array_to_json(risk_signal_codes)::text AS risk_signal_codes,
                    verified_category_code,
                    verified_route_code,
                    embedding_model,
                    embedding_dimensions,
                    embedding_version,
                    search_text_version,
                    embedding IS NOT NULL AS embedding_present,
                    vector_dims(embedding) AS embedding_vector_dimensions
                FROM retrieval_item
                WHERE id = ?
                """, itemId);
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

    private StructuredComplaint safetyThreatComplaint() {
        return new StructuredComplaint(
                "이웃 주민이 반복적으로 찾아오고 죽이겠다고 말한 상황",
                new ComplaintContext(PlaceType.RESIDENCE, RelationshipType.NEIGHBOR, TimePattern.REPEATED),
                List.of(
                        new ObservedFact(FactType.ACTION, "반복 방문", "반복적으로 찾아온다고 말함", Certainty.EXPLICIT),
                        new ObservedFact(FactType.HARM, "죽이겠다는 발언", "죽이겠다고 말했다고 진술", Certainty.EXPLICIT)
                ),
                List.of(
                        new RiskSignal(RiskSignalCode.DEATH_THREAT, "죽이겠다고 말함", Certainty.EXPLICIT),
                        new RiskSignal(RiskSignalCode.STALKING_PATTERN, "반복적으로 찾아옴", Certainty.EXPLICIT)
                ),
                List.of(new SubjectMatter(SubjectMatterCode.SAFETY_THREAT, "안전 위협", Certainty.EXPLICIT)),
                List.of(new RequestedAction(RequestedActionCode.REPORT, "신고 문의", Certainty.EXPLICIT)),
                OngoingStatus.REPEATED_AND_MAY_CONTINUE,
                List.of()
        );
    }

    private CategoryReference lostItemReference() {
        return new CategoryReference(
                "LOST_ITEM_REFERENCE",
                "유실물 관련 민원",
                "물건을 잃어버린 사용자가 검색 또는 처리 방법을 문의하는 민원 기준",
                List.of("지갑, 휴대전화, 가방 등 소지품 분실", "분실물 조회 또는 습득물 확인 요청"),
                List.of("절도 피해를 명시적으로 주장하는 경우"),
                List.of(),
                List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH, RequestedActionCode.CONSULT),
                true
        );
    }

    private OfficialGuideChunk lostItemGuideChunk() {
        return new OfficialGuideChunk(
                "경찰 민원 안내",
                "유실물 민원 안내",
                "분실한 물건에 대한 조회 및 신고는 유실물 관련 민원 절차를 통해 확인할 수 있다.",
                List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH, RequestedActionCode.REPORT),
                List.of()
        );
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

        @Override
        public EmbeddingResult embed(String searchText) {
            callCount.incrementAndGet();
            float[] vector = new float[1536];
            vector[0] = 0.125f;
            vector[1535] = 0.875f;
            return new EmbeddingResult(vector, "text-embedding-3-large", 1536, "embed-v1-large-1536");
        }

        void reset() {
            callCount.set(0);
        }

        int callCount() {
            return callCount.get();
        }
    }
}
