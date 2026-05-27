package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.application.evaluation.RetrievalHitAssessment;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class RetrievalHitRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RetrievalHitRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveAll(UUID queryId, RetrievalCandidates candidates, List<RetrievalHitAssessment> assessments) {
        Map<AssessmentKey, RetrievalHitAssessment> assessmentByHit = assessments.stream()
                .collect(Collectors.toMap(
                        assessment -> new AssessmentKey(assessment.channel(), assessment.itemId(), assessment.rank()),
                        Function.identity()
                ));
        saveHits(queryId, candidates.verifiedCases(), assessmentByHit);
        saveHits(queryId, candidates.categoryReferences(), assessmentByHit);
        saveHits(queryId, candidates.officialGuides(), assessmentByHit);
    }

    private void saveHits(
            UUID queryId,
            List<RetrievalSearchHit> hits,
            Map<AssessmentKey, RetrievalHitAssessment> assessmentByHit
    ) {
        for (RetrievalSearchHit hit : hits) {
            AssessmentKey key = new AssessmentKey(hit.itemType(), hit.itemId(), hit.rank());
            RetrievalHitAssessment assessment = assessmentByHit.get(key);
            if (assessment == null) {
                throw new IllegalStateException("Missing retrieval hit assessment.");
            }
            saveHit(queryId, hit, assessment);
        }
    }

    private void saveHit(UUID queryId, RetrievalSearchHit hit, RetrievalHitAssessment assessment) {
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
                    :passedChannelCutoff,
                    :rejectionReason
                )
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("queryId", queryId)
                .addValue("itemId", hit.itemId())
                .addValue("channel", hit.itemType().name())
                .addValue("resultRank", hit.rank())
                .addValue("cosineSimilarity", hit.cosineSimilarity())
                .addValue("passedChannelCutoff", assessment.passedChannelCutoff())
                .addValue("rejectionReason", assessment.rejectionReason() == null ? null : assessment.rejectionReason().name());

        jdbcTemplate.update(sql, parameters);
    }

    private record AssessmentKey(RetrievalItemType channel, long itemId, int rank) {
    }
}
