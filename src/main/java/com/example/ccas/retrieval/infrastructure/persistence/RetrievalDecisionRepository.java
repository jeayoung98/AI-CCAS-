package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceDecision;
import com.example.ccas.retrieval.domain.type.DecisionReasonCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class RetrievalDecisionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RetrievalDecisionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(UUID queryId, RetrievalEvidenceDecision decision) {
        String sql = """
                INSERT INTO retrieval_decision (
                    query_id,
                    evidence_status,
                    risk_signal_present,
                    best_case_score,
                    best_category_score,
                    best_guide_score,
                    category_margin,
                    case_category_agreement,
                    decision_reason_codes,
                    threshold_profile_version
                ) VALUES (
                    :queryId,
                    :evidenceStatus,
                    :riskSignalPresent,
                    :bestCaseScore,
                    :bestCategoryScore,
                    :bestGuideScore,
                    :categoryMargin,
                    :caseCategoryAgreement,
                    :decisionReasonCodes,
                    :thresholdProfileVersion
                )
                """;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("queryId", queryId)
                .addValue("evidenceStatus", decision.evidenceStatus().name())
                .addValue("riskSignalPresent", decision.riskSignalPresent())
                .addValue("bestCaseScore", decision.bestCaseScore())
                .addValue("bestCategoryScore", decision.bestCategoryScore())
                .addValue("bestGuideScore", decision.bestGuideScore())
                .addValue("categoryMargin", decision.categoryMargin())
                .addValue("caseCategoryAgreement", decision.caseCategoryAgreement())
                .addValue("decisionReasonCodes", decisionReasonCodes(decision))
                .addValue("thresholdProfileVersion", decision.thresholdProfileVersion());

        jdbcTemplate.update(sql, parameters);
    }

    private SqlArrayValue decisionReasonCodes(RetrievalEvidenceDecision decision) {
        String[] codes = decision.decisionReasonCodes().stream()
                .map(DecisionReasonCode::name)
                .toArray(String[]::new);
        return new SqlArrayValue("text", (Object[]) codes);
    }
}
