package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.application.search.RetrievalSearchException;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
public class PgVectorRetrievalSearchRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PgVectorRetrievalSearchRepository(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RetrievalSearchHit> searchByItemType(
            RetrievalItemType itemType,
            EmbeddingResult queryEmbedding,
            int limit
    ) {
        String sql = """
                SELECT
                    id,
                    item_type,
                    title,
                    structured_payload::text AS structured_payload,
                    subject_matter_codes,
                    requested_action_codes,
                    risk_signal_codes,
                    verified_category_code,
                    verified_route_code,
                    1 - (embedding <=> CAST(:queryEmbedding AS vector)) AS cosine_similarity,
                    ROW_NUMBER() OVER (ORDER BY embedding <=> CAST(:queryEmbedding AS vector), id) AS result_rank
                FROM retrieval_item
                WHERE item_type = :itemType
                  AND review_status = 'VERIFIED'
                  AND is_active = TRUE
                  AND embedding_version = :embeddingVersion
                ORDER BY embedding <=> CAST(:queryEmbedding AS vector), id
                LIMIT :limit
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("itemType", itemType.name())
                .addValue("queryEmbedding", new PGvector(queryEmbedding.vector()))
                .addValue("embeddingVersion", queryEmbedding.version())
                .addValue("limit", limit);

        try {
            return jdbcTemplate.query(sql, parameters, this::mapHit);
        } catch (RetrievalSearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetrievalSearchException("retrieval_item 벡터 검색 결과 매핑에 실패했습니다.", exception);
        }
    }

    private RetrievalSearchHit mapHit(ResultSet rs, int rowNum) throws SQLException {
        return new RetrievalSearchHit(
                rs.getLong("id"),
                RetrievalItemType.valueOf(rs.getString("item_type")),
                rs.getString("title"),
                readStructuredPayload(rs.getString("structured_payload")),
                textArray(rs.getArray("subject_matter_codes")),
                textArray(rs.getArray("requested_action_codes")),
                textArray(rs.getArray("risk_signal_codes")),
                rs.getString("verified_category_code"),
                rs.getString("verified_route_code"),
                rs.getDouble("cosine_similarity"),
                rs.getInt("result_rank")
        );
    }

    private JsonNode readStructuredPayload(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new RetrievalSearchException("structured_payload JSON 파싱에 실패했습니다.", exception);
        }
    }

    private List<String> textArray(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
