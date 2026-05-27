package com.example.ccas.retrieval.application.evaluation;

import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.config.RetrievalEvaluationProperties;
import com.example.ccas.retrieval.domain.type.DecisionReasonCode;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.HitRejectionReason;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalEvidenceEvaluatorTest {

    private final RetrievalEvidenceEvaluator evaluator = new RetrievalEvidenceEvaluator(new RetrievalEvaluationProperties(
            "provisional-unit-test-v1",
            true,
            0.80,
            0.75,
            0.70,
            0.05,
            0.67
    ));

    @Test
    void returnsCaseAndReferenceSupportedWhenAllChannelsAgree() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(hit(1, RetrievalItemType.VERIFIED_CASE, "LOST_ITEM", 0.95, 1)),
                List.of(hit(2, RetrievalItemType.CATEGORY_REFERENCE, "LOST_ITEM", 0.90, 1)),
                List.of(hit(3, RetrievalItemType.OFFICIAL_GUIDE, null, 0.88, 1))
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.CASE_AND_REFERENCE_SUPPORTED);
        assertThat(decision.bestCaseScore()).isEqualTo(0.95);
        assertThat(decision.bestCategoryScore()).isEqualTo(0.90);
        assertThat(decision.bestGuideScore()).isEqualTo(0.88);
        assertThat(decision.caseCategoryAgreement()).isEqualTo(1.0);
        assertThat(decision.decisionReasonCodes()).contains(
                DecisionReasonCode.RELIABLE_VERIFIED_CASE_FOUND,
                DecisionReasonCode.RELIABLE_CATEGORY_REFERENCE_FOUND,
                DecisionReasonCode.RELIABLE_OFFICIAL_GUIDE_FOUND
        );
    }

    @Test
    void appliesCutoffAndReturnsReferenceSupportedWhenCaseIsTooLow() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(hit(1, RetrievalItemType.VERIFIED_CASE, "LOST_ITEM", 0.60, 1)),
                List.of(hit(2, RetrievalItemType.CATEGORY_REFERENCE, "LOST_ITEM", 0.90, 1)),
                List.of(hit(3, RetrievalItemType.OFFICIAL_GUIDE, null, 0.88, 1))
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.REFERENCE_SUPPORTED);
        assertThat(decision.decisionReasonCodes()).contains(DecisionReasonCode.NO_RELIABLE_VERIFIED_CASE);
        assertThat(decision.hitAssessments()).filteredOn(RetrievalHitAssessment::passedChannelCutoff).hasSize(2);
        assertThat(decision.hitAssessments()).filteredOn(assessment -> !assessment.passedChannelCutoff())
                .singleElement()
                .extracting(RetrievalHitAssessment::rejectionReason)
                .isEqualTo(HitRejectionReason.BELOW_CASE_MIN_SIMILARITY);
    }

    @Test
    void returnsGuideOnlyWhenOnlyOfficialGuidePasses() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(),
                List.of(),
                List.of(hit(3, RetrievalItemType.OFFICIAL_GUIDE, null, 0.88, 1))
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.GUIDE_ONLY);
    }

    @Test
    void returnsInsufficientWhenAllChannelsAreAbsentOrBelowCutoff() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(hit(1, RetrievalItemType.VERIFIED_CASE, "LOST_ITEM", 0.60, 1)),
                List.of(hit(2, RetrievalItemType.CATEGORY_REFERENCE, "LOST_ITEM", 0.60, 1)),
                List.of(hit(3, RetrievalItemType.OFFICIAL_GUIDE, null, 0.60, 1))
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(decision.hitAssessments()).allSatisfy(assessment -> assertThat(assessment.passedChannelCutoff()).isFalse());
    }

    @Test
    void categoryMarginConflictHasAmbiguousPriority() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(),
                List.of(
                        hit(1, RetrievalItemType.CATEGORY_REFERENCE, "LOST_ITEM", 1.00, 1),
                        hit(2, RetrievalItemType.CATEGORY_REFERENCE, "CYBER_TRANSACTION", 0.98, 2)
                ),
                List.of(hit(3, RetrievalItemType.OFFICIAL_GUIDE, null, 0.90, 1))
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(decision.categoryMargin()).isCloseTo(0.02, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(decision.decisionReasonCodes()).contains(DecisionReasonCode.CATEGORY_MARGIN_TOO_SMALL);
    }

    @Test
    void caseCategoryConflictHasAmbiguousPriority() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(
                        hit(1, RetrievalItemType.VERIFIED_CASE, "LOST_ITEM", 1.00, 1),
                        hit(2, RetrievalItemType.VERIFIED_CASE, "CYBER_TRANSACTION", 0.99, 2),
                        hit(3, RetrievalItemType.VERIFIED_CASE, "SAFETY_THREAT", 0.98, 3)
                ),
                List.of(hit(4, RetrievalItemType.CATEGORY_REFERENCE, "LOST_ITEM", 0.90, 1)),
                List.of(hit(5, RetrievalItemType.OFFICIAL_GUIDE, null, 0.90, 1))
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(decision.caseCategoryAgreement()).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(decision.decisionReasonCodes()).contains(DecisionReasonCode.CASE_CATEGORY_CONFLICT);
    }

    @Test
    void caseReferenceMismatchHasAmbiguousPriority() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(hit(1, RetrievalItemType.VERIFIED_CASE, "LOST_ITEM", 0.95, 1)),
                List.of(hit(2, RetrievalItemType.CATEGORY_REFERENCE, "CYBER_TRANSACTION", 0.90, 1)),
                List.of(hit(3, RetrievalItemType.OFFICIAL_GUIDE, null, 0.88, 1))
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.AMBIGUOUS);
        assertThat(decision.decisionReasonCodes()).contains(DecisionReasonCode.CASE_REFERENCE_CATEGORY_MISMATCH);
    }

    @Test
    void caseAndCategoryWithoutGuideAreInsufficient() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(hit(1, RetrievalItemType.VERIFIED_CASE, "LOST_ITEM", 0.95, 1)),
                List.of(hit(2, RetrievalItemType.CATEGORY_REFERENCE, "LOST_ITEM", 0.90, 1)),
                List.of()
        ), false);

        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(decision.decisionReasonCodes()).contains(DecisionReasonCode.NO_RELIABLE_OFFICIAL_GUIDE);
    }

    @Test
    void riskSignalReasonIsPreservedRegardlessOfStatus() {
        RetrievalEvidenceDecision decision = evaluator.evaluate(new RetrievalCandidates(
                List.of(),
                List.of(),
                List.of()
        ), true);

        assertThat(decision.riskSignalPresent()).isTrue();
        assertThat(decision.evidenceStatus()).isEqualTo(EvidenceStatus.INSUFFICIENT_EVIDENCE);
        assertThat(decision.decisionReasonCodes()).contains(DecisionReasonCode.RISK_SIGNAL_PRESENT);
    }

    private RetrievalSearchHit hit(
            long itemId,
            RetrievalItemType itemType,
            String verifiedCategoryCode,
            double cosineSimilarity,
            int rank
    ) {
        JsonNode payload = JsonNodeFactory.instance.objectNode();
        return new RetrievalSearchHit(
                itemId,
                itemType,
                "title " + itemId,
                payload,
                List.of(),
                List.of(),
                List.of(),
                verifiedCategoryCode,
                null,
                cosineSimilarity,
                rank
        );
    }
}
