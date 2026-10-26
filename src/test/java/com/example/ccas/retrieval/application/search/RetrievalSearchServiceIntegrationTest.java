package com.example.ccas.retrieval.application.search;

import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.application.ingestion.KnowledgeSourceIngestionService;
import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalItemInsertRow;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.example.ccas.retrieval.infrastructure.persistence.PgVectorRetrievalSearchRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class RetrievalSearchServiceIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_search_test")
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
    private RetrievalSearchService searchService;

    @Autowired
    private RetrievalItemRepository itemRepository;

    @Autowired
    private KnowledgeSourceIngestionService sourceIngestionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired private PgVectorRetrievalSearchRepository searchRepository;

    @Test
    void recordsExactSearchPlansAndScaleTimingsOnSyntheticData() throws Exception {
        var output = java.nio.file.Path.of("build/reports/retrieval/exact-scale.txt");
        java.nio.file.Files.createDirectories(output.getParent());
        var report = new StringBuilder("Synthetic 1536-dimensional vectors (two nonzero components), no API calls.\n")
                .append("Single VERIFIED_CASE channel; equal K=5; JDBC + mapping wall time; 3 warmups, 20 samples.\n")
                .append("PostgreSQL: ").append(jdbcTemplate.queryForObject("SELECT version()", String.class)).append('\n');
        for (int size : new int[]{100, 1000, 10000}) {
            jdbcTemplate.update("DELETE FROM retrieval_item");
            long seed = saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "scale-seed", "synthetic",
                    ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1, 0));
            jdbcTemplate.update("""
                    INSERT INTO retrieval_item (item_type, source_id, external_key, title, structured_payload, search_text,
                        subject_matter_codes, requested_action_codes, risk_signal_codes, review_status, is_active,
                        embedding, embedding_model, embedding_dimensions, structure_version, search_text_version, embedding_version)
                    SELECT CASE g%3 WHEN 0 THEN 'VERIFIED_CASE' WHEN 1 THEN 'CATEGORY_REFERENCE' ELSE 'OFFICIAL_GUIDE' END,
                        source_id, 'scale-'||g, title, structured_payload, search_text, subject_matter_codes,
                        requested_action_codes, risk_signal_codes, CASE WHEN g%13=0 THEN 'DRAFT' ELSE 'VERIFIED' END,
                        g%11<>0, ('[' || cos(g)::text || ',' || sin(g)::text || repeat(',0',1534) || ']')::vector,
                        embedding_model, embedding_dimensions, structure_version, search_text_version,
                        CASE WHEN g%7=0 THEN 'other-version' ELSE embedding_version END
                    FROM retrieval_item CROSS JOIN generate_series(1, ?) AS g WHERE id=?
                    """, size - 1, seed);
            jdbcTemplate.execute("ANALYZE retrieval_item");
            var baseline = new java.util.ArrayList<Double>();
            var optimized = new java.util.ArrayList<Double>();
            for (int round = -3; round < 20; round++) {
                for (int offset = 0; offset < 2; offset++) {
                    boolean original = Math.floorMod(round + offset, 2) == 0;
                    long start = System.nanoTime();
                    var hits = original ? searchRepository.searchByItemType(RetrievalItemType.VERIFIED_CASE, queryEmbedding(), 5)
                            : searchRepository.searchExactCandidates(RetrievalItemType.VERIFIED_CASE, queryEmbedding(), 5);
                    double ms = (System.nanoTime() - start) / 1_000_000.0;
                    assertThat(hits).hasSize(5);
                    if (round >= 0) (original ? baseline : optimized).add(ms);
                }
            }
            assertThat(searchRepository.searchExactCandidates(RetrievalItemType.VERIFIED_CASE, queryEmbedding(), 5))
                    .isEqualTo(searchRepository.searchByItemType(RetrievalItemType.VERIFIED_CASE, queryEmbedding(), 5));
            baseline.sort(Double::compare); optimized.sort(Double::compare);
            report.append("\nrows=").append(size)
                    .append(" baseline p50/p95 ms=").append(baseline.get(9)).append('/').append(baseline.get(18))
                    .append(" bounded p50/p95 ms=").append(optimized.get(9)).append('/').append(optimized.get(18)).append('\n');
            for (boolean original : List.of(true, false)) {
                report.append(original ? "BASELINE\n" : "BOUNDED EXACT\n");
                report.append(String.join("\n", searchRepository.explainSearch(RetrievalItemType.VERIFIED_CASE,
                        queryEmbedding(), 5, null, original))).append('\n');
            }
        }
        java.nio.file.Files.writeString(output, report.toString().replaceAll("'\\[[^']*\\]'::vector", "'<synthetic query vector>'::vector"));
    }

    @Test
    void codeChannelExpandsCandidatePoolAndRetainsEligibilityFilters() {
        long wrong = saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "wrong", "신고",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1, 0));
        long right = saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "right", "조회",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(.98f, .199f));
        long inactive = saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "inactive-code", "inactive",
                ReviewStatus.VERIFIED, false, "embed-v1-large-1536", vector(1, 0));
        long draft = saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "draft-code", "draft",
                ReviewStatus.DRAFT, true, "embed-v1-large-1536", vector(1, 0));
        long stale = saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "old-code", "old",
                ReviewStatus.VERIFIED, true, "old-version", vector(1, 0));
        for (long id : List.of(right, inactive, draft, stale))
            jdbcTemplate.update("UPDATE retrieval_item SET requested_action_codes=ARRAY['SEARCH'] WHERE id=?", id);
        var metadata = new RetrievalMetadata(List.of("SAFETY_THREAT"), List.of("SEARCH"), List.of("WEAPON"), null, null, null);
        var vector = searchRepository.searchExactCandidates(RetrievalItemType.VERIFIED_CASE, queryEmbedding(), 1);
        var codes = searchRepository.searchCodeCandidates(RetrievalItemType.VERIFIED_CASE, queryEmbedding(), 1, metadata);
        assertThat(vector).extracting(RetrievalSearchHit::itemId).containsExactly(wrong);
        assertThat(codes).extracting(RetrievalSearchHit::itemId).containsExactly(right);
        var settings = new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(2, 2, 2),
                RetrievalSearchProperties.Strategy.CODE_RRF, 1, .3, 60);
        var hits = new RetrievalSearchService(searchRepository, settings).retrieveCandidates(queryEmbedding(), metadata).verifiedCases();
        // Opposite ranks tie under equal-weight RRF; deterministic id tie-break retains baseline first.
        assertThat(hits).extracting(RetrievalSearchHit::itemId).containsExactly(wrong, right);
        assertThat(hits.get(1).cosineSimilarity()).isLessThan(1);
    }

    private long verifiedCaseSourceId;
    private long categoryReferenceSourceId;
    private long officialGuideSourceId;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");

        verifiedCaseSourceId = createSource(KnowledgeSourceType.TEAM_VERIFIED_CASESET, "검수 사례집");
        categoryReferenceSourceId = createSource(KnowledgeSourceType.CATEGORY_POLICY, "유형 기준");
        officialGuideSourceId = createSource(KnowledgeSourceType.OFFICIAL_DOCUMENT, "공식 안내");
    }

    @Test
    void searchesVerifiedCasesByCosineSimilarityRank() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "very-similar", "가장 유사한 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "similar", "유사한 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(0.8f, 0.6f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "less-similar", "덜 유사한 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(0.6f, 0.8f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "unrelated", "무관한 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(0.0f, 1.0f));

        RetrievalCandidates candidates = searchService.retrieveCandidates(queryEmbedding());

        assertThat(candidates.verifiedCases())
                .extracting(RetrievalSearchHit::title)
                .containsExactly("가장 유사한 사례", "유사한 사례", "덜 유사한 사례", "무관한 사례");
        assertThat(candidates.verifiedCases())
                .extracting(RetrievalSearchHit::rank)
                .containsExactly(1, 2, 3, 4);
        assertThat(candidates.verifiedCases())
                .extracting(RetrievalSearchHit::itemType)
                .containsOnly(RetrievalItemType.VERIFIED_CASE);
        assertThat(candidates.verifiedCases().get(0).cosineSimilarity()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(candidates.verifiedCases().get(1).cosineSimilarity()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void keepsSearchResultsSeparatedByChannel() {
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categoryReferenceSourceId, "category-high", "매우 유사한 기준",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "case-lower", "덜 유사한 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(0.6f, 0.8f));
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, officialGuideSourceId, "guide-high", "매우 유사한 안내",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));

        RetrievalCandidates candidates = searchService.retrieveCandidates(queryEmbedding());

        assertThat(candidates.verifiedCases()).extracting(RetrievalSearchHit::title).containsExactly("덜 유사한 사례");
        assertThat(candidates.categoryReferences()).extracting(RetrievalSearchHit::title).containsExactly("매우 유사한 기준");
        assertThat(candidates.officialGuides()).extracting(RetrievalSearchHit::title).containsExactly("매우 유사한 안내");
    }

    @Test
    void appliesConfiguredTopKPerChannel() {
        for (int i = 0; i < 6; i++) {
            saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "case-" + i, "검수 사례 " + i,
                    ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
        }
        for (int i = 0; i < 4; i++) {
            saveItem(RetrievalItemType.CATEGORY_REFERENCE, categoryReferenceSourceId, "category-" + i, "유형 기준 " + i,
                    ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
            saveItem(RetrievalItemType.OFFICIAL_GUIDE, officialGuideSourceId, "guide-" + i, "공식 안내 " + i,
                    ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
        }

        RetrievalCandidates candidates = searchService.retrieveCandidates(queryEmbedding());

        assertThat(candidates.verifiedCases()).hasSize(5);
        assertThat(candidates.categoryReferences()).hasSize(3);
        assertThat(candidates.officialGuides()).hasSize(3);
    }

    @Test
    void filtersNonVerifiedReviewStatuses() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "draft", "작성 중 사례",
                ReviewStatus.DRAFT, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "review-required", "검수 필요 사례",
                ReviewStatus.REVIEW_REQUIRED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "rejected", "거절 사례",
                ReviewStatus.REJECTED, true, "embed-v1-large-1536", vector(1.0f, 0.0f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "verified", "검수 완료 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(0.6f, 0.8f));

        RetrievalCandidates candidates = searchService.retrieveCandidates(queryEmbedding());

        assertThat(candidates.verifiedCases()).extracting(RetrievalSearchHit::title).containsExactly("검수 완료 사례");
    }

    @Test
    void filtersInactiveItems() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "inactive", "비활성 사례",
                ReviewStatus.VERIFIED, false, "embed-v1-large-1536", vector(1.0f, 0.0f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "active", "활성 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(0.6f, 0.8f));

        RetrievalCandidates candidates = searchService.retrieveCandidates(queryEmbedding());

        assertThat(candidates.verifiedCases()).extracting(RetrievalSearchHit::title).containsExactly("활성 사례");
    }

    @Test
    void filtersDifferentEmbeddingVersion() {
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "different-version", "다른 버전 사례",
                ReviewStatus.VERIFIED, true, "embed-v2-other", vector(1.0f, 0.0f));
        saveItem(RetrievalItemType.VERIFIED_CASE, verifiedCaseSourceId, "same-version", "같은 버전 사례",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(0.6f, 0.8f));

        RetrievalCandidates candidates = searchService.retrieveCandidates(queryEmbedding());

        assertThat(candidates.verifiedCases()).extracting(RetrievalSearchHit::title).containsExactly("같은 버전 사례");
    }

    @Test
    void returnsStructuredPayloadAsJsonNode() {
        saveItem(RetrievalItemType.CATEGORY_REFERENCE, categoryReferenceSourceId, "category-payload", "유실물 기준",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f),
                "{\"categoryName\":\"유실물 관련 민원\"}");
        saveItem(RetrievalItemType.OFFICIAL_GUIDE, officialGuideSourceId, "guide-payload", "유실물 안내",
                ReviewStatus.VERIFIED, true, "embed-v1-large-1536", vector(1.0f, 0.0f),
                "{\"sectionTitle\":\"유실물 민원 안내\"}");

        RetrievalCandidates candidates = searchService.retrieveCandidates(queryEmbedding());

        assertThat(candidates.categoryReferences().getFirst().structuredPayload().get("categoryName").asText())
                .isEqualTo("유실물 관련 민원");
        assertThat(candidates.officialGuides().getFirst().structuredPayload().get("sectionTitle").asText())
                .isEqualTo("유실물 민원 안내");
    }

    @Test
    void validatesQueryEmbeddingBeforeSearch() {
        assertThatThrownBy(() -> searchService.retrieveCandidates(new EmbeddingResult(new float[1536], "model", 1536, "version")))
                .isInstanceOf(InvalidQueryEmbeddingException.class).hasMessage("query vector must be nonzero");
        assertThatThrownBy(() -> searchService.retrieveCandidates(new EmbeddingResult(vector(Float.NaN, 1), "model", 1536, "version")))
                .isInstanceOf(InvalidQueryEmbeddingException.class).hasMessage("query vector must be finite");
        assertThatThrownBy(() -> searchService.retrieveCandidates(null))
                .isInstanceOf(InvalidQueryEmbeddingException.class);
        assertThatThrownBy(() -> searchService.retrieveCandidates(new EmbeddingResult(null, "model", 1536, "version")))
                .isInstanceOf(InvalidQueryEmbeddingException.class)
                .hasMessage("query embedding vector는 null일 수 없습니다.");
        assertThatThrownBy(() -> searchService.retrieveCandidates(new EmbeddingResult(new float[1535], "model", 1535, "version")))
                .isInstanceOf(InvalidQueryEmbeddingException.class)
                .hasMessage("query embedding vector는 1536차원이어야 합니다.");
        assertThatThrownBy(() -> searchService.retrieveCandidates(new EmbeddingResult(vector(1.0f, 0.0f), "model", 1535, "version")))
                .isInstanceOf(InvalidQueryEmbeddingException.class)
                .hasMessage("query embedding dimensions는 1536이어야 합니다.");
        assertThatThrownBy(() -> searchService.retrieveCandidates(new EmbeddingResult(vector(1.0f, 0.0f), "model", 1536, " ")))
                .isInstanceOf(InvalidQueryEmbeddingException.class)
                .hasMessage("query embedding version은 비어 있을 수 없습니다.");
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
            ReviewStatus reviewStatus,
            boolean active,
            String embeddingVersion,
            float[] vector
    ) {
        return saveItem(itemType, sourceId, externalKey, title, reviewStatus, active, embeddingVersion, vector,
                "{\"testPayload\":\"" + externalKey + "\"}");
    }

    private long saveItem(
            RetrievalItemType itemType,
            long sourceId,
            String externalKey,
            String title,
            ReviewStatus reviewStatus,
            boolean active,
            String embeddingVersion,
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
                List.of(itemType == RetrievalItemType.CATEGORY_REFERENCE ? "LOST_ITEM" : "SAFETY_THREAT"),
                List.of("REPORT"),
                List.of(),
                null,
                null,
                null,
                itemType == RetrievalItemType.CATEGORY_REFERENCE ? "LOST_ITEM_REFERENCE" : null,
                null,
                reviewStatus,
                active,
                new EmbeddingResult(vector, "text-embedding-3-large", 1536, embeddingVersion),
                "complaint-structure-v1",
                "test-search-text-version"
        ));
    }

    private EmbeddingResult queryEmbedding() {
        return new EmbeddingResult(vector(1.0f, 0.0f), "text-embedding-3-large", 1536, "embed-v1-large-1536");
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[1536];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }
}
