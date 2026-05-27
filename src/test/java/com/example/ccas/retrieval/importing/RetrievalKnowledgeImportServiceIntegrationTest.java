package com.example.ccas.retrieval.importing;

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
import org.springframework.dao.DataAccessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
class RetrievalKnowledgeImportServiceIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_import_test")
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
    private RetrievalKnowledgeImportService importService;

    @Autowired
    private RetrievalKnowledgeImportLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SyntheticImportEmbeddingPort embeddingPort;

    @TempDir
    Path tempDir;

    private Path fixturePath;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM retrieval_decision");
        jdbcTemplate.update("DELETE FROM retrieval_hit");
        jdbcTemplate.update("DELETE FROM retrieval_query");
        jdbcTemplate.update("DELETE FROM retrieval_item");
        jdbcTemplate.update("DELETE FROM knowledge_source");
        embeddingPort.reset();
        fixturePath = Path.of("src/test/resources/retrieval/import/synthetic/synthetic-import-v1.json");
    }

    @Test
    void defaultApplicationContextDoesNotRunImportAutomatically() {
        assertThat(countRows("knowledge_source")).isZero();
        assertThat(countRows("retrieval_item")).isZero();
        assertThat(embeddingPort.callCount()).isZero();
    }

    @Test
    void importsSyntheticFixtureWhenExplicitlyAllowed() {
        RetrievalKnowledgeImportReport report = importService.importDataset(
                fixturePath,
                new ImportExecutionOptions(true, false)
        );

        assertThat(report.datasetId()).isEqualTo("synthetic-import-v1");
        assertThat(report.synthetic()).isTrue();
        assertThat(report.dryRun()).isFalse();
        assertThat(report.sourceCount()).isEqualTo(3);
        assertThat(report.totalItemCount()).isEqualTo(4);
        assertThat(report.verifiedCaseCount()).isEqualTo(1);
        assertThat(report.categoryReferenceCount()).isEqualTo(2);
        assertThat(report.officialGuideCount()).isEqualTo(1);
        assertThat(report.searchableVerifiedItemCount()).isEqualTo(3);
        assertThat(report.importedExternalKeys()).containsExactly(
                "synthetic-case-lost-wallet",
                "synthetic-category-lost-item",
                "synthetic-category-draft",
                "synthetic-guide-lost-item"
        );
        assertThat(report.nonSearchableExternalKeys()).containsExactly("synthetic-category-draft");
        assertThat(countRows("knowledge_source")).isEqualTo(3);
        assertThat(countRows("retrieval_item")).isEqualTo(4);
        assertThat(embeddingPort.callCount()).isEqualTo(4);
    }

    @Test
    void rejectsSyntheticFixtureByDefault() {
        assertThatThrownBy(() -> importService.importDataset(fixturePath, new ImportExecutionOptions(false, false)))
                .isInstanceOf(RetrievalKnowledgeImportException.class)
                .hasMessageContaining("Synthetic import dataset is not allowed");

        assertNoPersistenceAndNoEmbedding();
    }

    @Test
    void dryRunValidatesAndReturnsReportWithoutPersistenceOrEmbedding() {
        RetrievalKnowledgeImportReport report = importService.importDataset(
                fixturePath,
                new ImportExecutionOptions(true, true)
        );

        assertThat(report.dryRun()).isTrue();
        assertThat(report.totalItemCount()).isEqualTo(4);
        assertThat(report.importedExternalKeys()).isEmpty();
        assertThat(report.nonSearchableExternalKeys()).containsExactly("synthetic-category-draft");
        assertNoPersistenceAndNoEmbedding();
    }

    @Test
    void rejectsNonSyntheticDatasetWithoutReviewMetadata() throws Exception {
        Path path = writeDataset(readFixture().replace("\"synthetic\": true", "\"synthetic\": false"));

        assertThatThrownBy(() -> importService.importDataset(path, new ImportExecutionOptions(false, true)))
                .isInstanceOf(RetrievalKnowledgeImportException.class)
                .hasMessageContaining("reviewedBy");

        assertNoPersistenceAndNoEmbedding();
    }

    @Test
    void rejectsStructureVersionMismatchBeforePersistenceAndEmbedding() throws Exception {
        Path path = writeDataset(readFixture().replace(
                "\"structureVersion\": \"complaint-structure-v1\"",
                "\"structureVersion\": \"other-structure-version\""
        ));

        assertThatThrownBy(() -> importService.importDataset(path, new ImportExecutionOptions(true, false)))
                .isInstanceOf(RetrievalKnowledgeImportException.class)
                .hasMessageContaining("structureVersion");

        assertNoPersistenceAndNoEmbedding();
    }

    @Test
    void rejectsEmbeddingVersionMismatchBeforePersistenceAndEmbedding() throws Exception {
        Path path = writeDataset(readFixture().replace(
                "\"embeddingVersion\": \"embed-v1-large-1536\"",
                "\"embeddingVersion\": \"other-embedding-version\""
        ));

        assertThatThrownBy(() -> importService.importDataset(path, new ImportExecutionOptions(true, false)))
                .isInstanceOf(RetrievalKnowledgeImportException.class)
                .hasMessageContaining("embeddingVersion");

        assertNoPersistenceAndNoEmbedding();
    }

    @Test
    void rejectsSourceItemTypeMismatchBeforePersistenceAndEmbedding() throws Exception {
        assertInvalidFixture(
                readFixture().replace("\"sourceType\": \"OFFICIAL_DOCUMENT\"", "\"sourceType\": \"TEAM_VERIFIED_CASESET\""),
                "cannot contain item type OFFICIAL_GUIDE"
        );
        assertInvalidFixture(
                readFixture().replace("\"sourceType\": \"CATEGORY_POLICY\"", "\"sourceType\": \"OFFICIAL_DOCUMENT\""),
                "cannot contain item type CATEGORY_REFERENCE"
        );
        assertInvalidFixture(
                readFixture().replace("\"sourceType\": \"TEAM_VERIFIED_CASESET\"", "\"sourceType\": \"CATEGORY_POLICY\""),
                "cannot contain item type VERIFIED_CASE"
        );
    }

    @Test
    void rejectsPayloadCombinationErrorBeforePersistenceAndEmbedding() throws Exception {
        String invalid = readFixture().replaceFirst(
                "\"officialGuideChunk\": null,\\s*\"verifiedCategoryCode\": null",
                "\"officialGuideChunk\": {\"documentTitle\":\"Synthetic extra\",\"sectionTitle\":\"Synthetic extra\",\"chunkText\":\"Synthetic extra\",\"subjectMatters\":[\"LOST_ITEM\"],\"relatedActions\":[],\"relatedRiskSignals\":[]},\n          \"verifiedCategoryCode\": null"
        );

        assertInvalidFixture(invalid, "exactly one typed payload");
    }

    @Test
    void duplicateImportFailsWithoutOverwritingExistingItems() {
        importService.importDataset(fixturePath, new ImportExecutionOptions(true, false));
        int itemCountAfterFirstImport = countRows("retrieval_item");

        assertThatThrownBy(() -> importService.importDataset(fixturePath, new ImportExecutionOptions(true, false)))
                .isInstanceOf(DataAccessException.class);

        assertThat(countRows("retrieval_item")).isEqualTo(itemCountAfterFirstImport);
    }

    private void assertInvalidFixture(String json, String message) throws Exception {
        Path path = writeDataset(json);

        assertThatThrownBy(() -> importService.importDataset(path, new ImportExecutionOptions(true, false)))
                .isInstanceOf(RetrievalKnowledgeImportException.class)
                .hasMessageContaining(message);

        assertNoPersistenceAndNoEmbedding();
    }

    private void assertNoPersistenceAndNoEmbedding() {
        assertThat(countRows("knowledge_source")).isZero();
        assertThat(countRows("retrieval_item")).isZero();
        assertThat(embeddingPort.callCount()).isZero();
    }

    private int countRows(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
    }

    private String readFixture() throws Exception {
        return Files.readString(fixturePath);
    }

    private Path writeDataset(String content) throws Exception {
        Path path = tempDir.resolve("import-dataset.json");
        Files.writeString(path, content);
        return path;
    }

    @TestConfiguration
    static class SyntheticImportEmbeddingConfig {

        @Bean
        @Primary
        SyntheticImportEmbeddingPort syntheticImportEmbeddingPort() {
            return new SyntheticImportEmbeddingPort();
        }
    }

    static class SyntheticImportEmbeddingPort implements EmbeddingPort {

        private final AtomicInteger callCount = new AtomicInteger();
        private final Map<String, float[]> vectors = Map.of(
                "SYNTHETIC_IMPORT_VECTOR:lost", vector(1.0f, 0.0f),
                "SYNTHETIC_IMPORT_VECTOR:draft", vector(0.0f, 1.0f)
        );

        @Override
        public EmbeddingResult embed(String searchText) {
            callCount.incrementAndGet();
            return new EmbeddingResult(vectorFor(searchText), "synthetic-import-fake-embedding", 1536, "embed-v1-large-1536");
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
                    .orElseThrow(() -> new IllegalArgumentException("No synthetic import vector token."));
        }

        private static float[] vector(float first, float second) {
            float[] vector = new float[1536];
            vector[0] = first;
            vector[1] = second;
            return vector;
        }
    }
}
