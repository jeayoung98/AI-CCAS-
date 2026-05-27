package com.example.ccas.retrieval.experiment.persistence;

import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.experiment.report.SimilaritySearchHit;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ExperimentGeneralComplaintRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExperimentGeneralComplaintRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recreateTable() {
        jdbcTemplate.getJdbcTemplate().execute("DROP TABLE IF EXISTS experiment_general_complaint_item");
        jdbcTemplate.getJdbcTemplate().execute("""
                CREATE TABLE experiment_general_complaint_item (
                    complaint_id BIGINT PRIMARY KEY,
                    title TEXT NOT NULL,
                    embedding VECTOR(1536) NOT NULL
                )
                """);
    }

    public void save(long complaintId, String title, EmbeddingResult embeddingResult) {
        String sql = """
                INSERT INTO experiment_general_complaint_item (complaint_id, title, embedding)
                VALUES (:complaintId, :title, :embedding)
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("complaintId", complaintId)
                .addValue("title", title)
                .addValue("embedding", new PGvector(embeddingResult.vector()));
        jdbcTemplate.update(sql, parameters);
    }

    public List<SimilaritySearchHit> searchTop5(long queryId, EmbeddingResult queryEmbedding) {
        String sql = """
                SELECT complaint_id,
                       title,
                       1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
                FROM experiment_general_complaint_item
                WHERE complaint_id <> :queryId
                ORDER BY embedding <=> CAST(:queryEmbedding AS vector), complaint_id
                LIMIT 5
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("queryId", queryId)
                .addValue("queryEmbedding", new PGvector(queryEmbedding.vector()));

        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> new SimilaritySearchHit(
                rs.getLong("complaint_id"),
                rs.getString("title"),
                rs.getDouble("similarity")
        ));
    }
}
