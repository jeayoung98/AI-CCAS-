package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class RetrievalHitRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RetrievalHitRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(UUID queryId, RetrievalCandidates candidates) {
        saveHits(queryId, candidates.verifiedCases());
        saveHits(queryId, candidates.categoryReferences());
        saveHits(queryId, candidates.officialGuides());
    }

    private void saveHits(UUID queryId, List<RetrievalSearchHit> hits) {
        for (RetrievalSearchHit hit : hits) {
            saveHit(queryId, hit);
        }
    }

    private void saveHit(UUID queryId, RetrievalSearchHit hit) {
        String sql = """
                INSERT INTO retrieval_hit (
                    query_id,
                    item_id,
                    channel,
                    result_rank,
                    cosine_similarity,
                    passed_channel_cutoff,
                    rejection_reason
                ) VALUES (
                    :queryId,
                    :itemId,
                    :channel,
                    :resultRank,
                    :cosineSimilarity,
                    NULL,
                    NULL
                )
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("queryId", queryId)
                .addValue("itemId", hit.itemId())
                .addValue("channel", hit.itemType().name())
                .addValue("resultRank", hit.rank())
                .addValue("cosineSimilarity", hit.cosineSimilarity());

        jdbcTemplate.update(sql, parameters);
    }
}
