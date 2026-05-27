package com.example.ccas.retrieval.infrastructure.persistence;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

@Repository
public class RetrievalItemRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RetrievalItemRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long save(RetrievalItemInsertRow row) {
        String sql = """
                INSERT INTO retrieval_item (
                    item_type,
                    source_id,
                    external_key,
                    title,
                    masked_text,
                    structured_payload,
                    search_text,
                    subject_matter_codes,
                    requested_action_codes,
                    risk_signal_codes,
                    place_type,
                    relationship_type,
                    ongoing_status,
                    verified_category_code,
                    verified_route_code,
                    review_status,
                    is_active,
                    embedding,
                    embedding_model,
                    embedding_dimensions,
                    structure_version,
                    search_text_version,
                    embedding_version
                ) VALUES (
                    :itemType,
                    :sourceId,
                    :externalKey,
                    :title,
                    :maskedText,
                    CAST(:structuredPayloadJson AS jsonb),
                    :searchText,
                    :subjectMatterCodes,
                    :requestedActionCodes,
                    :riskSignalCodes,
                    :placeType,
                    :relationshipType,
                    :ongoingStatus,
                    :verifiedCategoryCode,
                    :verifiedRouteCode,
                    :reviewStatus,
                    :active,
                    :embedding,
                    :embeddingModel,
                    :embeddingDimensions,
                    :structureVersion,
                    :searchTextVersion,
                    :embeddingVersion
                )
                RETURNING id
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("itemType", row.itemType().name())
                .addValue("sourceId", row.sourceId())
                .addValue("externalKey", row.externalKey())
                .addValue("title", row.title())
                .addValue("maskedText", row.maskedText())
                .addValue("structuredPayloadJson", row.structuredPayloadJson())
                .addValue("searchText", row.searchText())
                .addValue("subjectMatterCodes", textArray(row.subjectMatterCodes()))
                .addValue("requestedActionCodes", textArray(row.requestedActionCodes()))
                .addValue("riskSignalCodes", textArray(row.riskSignalCodes()))
                .addValue("placeType", row.placeType())
                .addValue("relationshipType", row.relationshipType())
                .addValue("ongoingStatus", row.ongoingStatus())
                .addValue("verifiedCategoryCode", row.verifiedCategoryCode())
                .addValue("verifiedRouteCode", row.verifiedRouteCode())
                .addValue("reviewStatus", row.reviewStatus().name())
                .addValue("active", row.active())
                .addValue("embedding", new PGvector(row.embeddingResult().vector()))
                .addValue("embeddingModel", row.embeddingResult().model())
                .addValue("embeddingDimensions", row.embeddingResult().dimensions())
                .addValue("structureVersion", row.structureVersion())
                .addValue("searchTextVersion", row.searchTextVersion())
                .addValue("embeddingVersion", row.embeddingResult().version());

        Long id = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        if (id == null) {
            throw new IllegalStateException("retrieval_item id가 반환되지 않았습니다.");
        }
        return id;
    }

    private SqlArrayValue textArray(Iterable<String> values) {
        return new SqlArrayValue("text", (Object[]) toArray(values));
    }

    private String[] toArray(Iterable<String> values) {
        if (values instanceof java.util.Collection<String> collection) {
            return collection.toArray(String[]::new);
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        for (String value : values) {
            result.add(value);
        }
        return result.toArray(String[]::new);
    }
}
