package com.example.ccas.retrieval.infrastructure.persistence;

import com.pgvector.PGvector;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

@Repository
public class RetrievalQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RetrievalQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(RetrievalQueryInsertRow row) {
        String sql = """
                INSERT INTO retrieval_query (
                    id,
                    input_source,
                    masking_completed,
                    masked_text,
                    structured_payload,
                    search_text,
                    subject_matter_codes,
                    requested_action_codes,
                    risk_signal_codes,
                    place_type,
                    relationship_type,
                    ongoing_status,
                    embedding,
                    embedding_model,
                    embedding_dimensions,
                    structure_version,
                    search_text_version,
                    embedding_version
                ) VALUES (
                    :id,
                    :inputSource,
                    :maskingCompleted,
                    :maskedText,
                    CAST(:structuredPayloadJson AS jsonb),
                    :searchText,
                    :subjectMatterCodes,
                    :requestedActionCodes,
                    :riskSignalCodes,
                    :placeType,
                    :relationshipType,
                    :ongoingStatus,
                    :embedding,
                    :embeddingModel,
                    :embeddingDimensions,
                    :structureVersion,
                    :searchTextVersion,
                    :embeddingVersion
                )
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", row.id())
                .addValue("inputSource", row.inputSource().name())
                .addValue("maskingCompleted", row.maskingCompleted())
                .addValue("maskedText", row.maskedText())
                .addValue("structuredPayloadJson", row.structuredPayloadJson())
                .addValue("searchText", row.searchText())
                .addValue("subjectMatterCodes", textArray(row.metadata().subjectMatterCodes()))
                .addValue("requestedActionCodes", textArray(row.metadata().requestedActionCodes()))
                .addValue("riskSignalCodes", textArray(row.metadata().riskSignalCodes()))
                .addValue("placeType", row.metadata().placeType())
                .addValue("relationshipType", row.metadata().relationshipType())
                .addValue("ongoingStatus", row.metadata().ongoingStatus())
                .addValue("embedding", new PGvector(row.embeddingResult().vector()))
                .addValue("embeddingModel", row.embeddingResult().model())
                .addValue("embeddingDimensions", row.embeddingResult().dimensions())
                .addValue("structureVersion", row.structureVersion())
                .addValue("searchTextVersion", row.searchTextVersion())
                .addValue("embeddingVersion", row.embeddingResult().version());

        jdbcTemplate.update(sql, parameters);
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
