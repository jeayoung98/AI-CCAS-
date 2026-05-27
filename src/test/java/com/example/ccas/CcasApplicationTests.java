package com.example.ccas;

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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class CcasApplicationTests {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("ccas_test")
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
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void flywayCreatesPgvectorRetrievalSchema() {
        assertThat(countRows("SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'")).isEqualTo(1);
        assertThat(tableExists("retrieval_item")).isTrue();
        assertThat(tableExists("retrieval_query")).isTrue();
        assertThat(embeddingColumnType("retrieval_item")).isEqualTo("vector(1536)");
        assertThat(embeddingColumnType("retrieval_query")).isEqualTo("vector(1536)");
    }

    private int countRows(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        assertThat(count).isNotNull();
        return count;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
                """, Integer.class, tableName);
        assertThat(count).isNotNull();
        return count == 1;
    }

    private String embeddingColumnType(String tableName) {
        String columnType = jdbcTemplate.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public'
                  AND c.relname = ?
                  AND a.attname = 'embedding'
                  AND a.attnum > 0
                """, String.class, tableName);
        assertThat(columnType).isNotNull();
        return columnType;
    }

}
