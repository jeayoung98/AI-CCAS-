package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class KnowledgeSourceRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public KnowledgeSourceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long save(CreateKnowledgeSourceCommand command) {
        String sql = """
                INSERT INTO knowledge_source (
                    source_type,
                    title,
                    source_organization,
                    source_url,
                    published_at,
                    checked_at
                ) VALUES (
                    :sourceType,
                    :title,
                    :sourceOrganization,
                    :sourceUrl,
                    :publishedAt,
                    :checkedAt
                )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("sourceType", command.sourceType().name())
                .addValue("title", command.title())
                .addValue("sourceOrganization", command.sourceOrganization())
                .addValue("sourceUrl", command.sourceUrl())
                .addValue("publishedAt", command.publishedAt())
                .addValue("checkedAt", command.checkedAt());

        Long id = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        if (id == null) {
            throw new IllegalStateException("knowledge_source id가 반환되지 않았습니다.");
        }
        return id;
    }

    public Optional<KnowledgeSourceRecord> findById(long id) {
        String sql = """
                SELECT id, source_type, title
                FROM knowledge_source
                WHERE id = :id
                """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource("id", id), (rs, rowNum) -> new KnowledgeSourceRecord(
                rs.getLong("id"),
                KnowledgeSourceType.valueOf(rs.getString("source_type")),
                rs.getString("title")
        )).stream().findFirst();
    }
}
