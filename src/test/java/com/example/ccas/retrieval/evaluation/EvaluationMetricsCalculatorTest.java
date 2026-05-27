package com.example.ccas.retrieval.evaluation;

import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.FactType;
import com.example.ccas.retrieval.domain.type.InputSource;
import com.example.ccas.retrieval.domain.type.OngoingStatus;
import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.example.ccas.retrieval.domain.type.TimePattern;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class EvaluationMetricsCalculatorTest {

    private final EvaluationMetricsCalculator calculator = new EvaluationMetricsCalculator();

    @Test
    void calculatesChannelMetricsAndDecisionMetrics() {
        EvaluationDataset dataset = new EvaluationDataset(
                "metric-test",
                "synthetic metric test",
                true,
                "complaint-structure-v1",
                "embed-v1-large-1536",
                "provisional-v1",
                List.of(),
                List.of(
                        query("q1", List.of("case-a"), List.of("cat-a"), List.of("guide-a"), EvidenceStatus.REFERENCE_SUPPORTED, true, false, false),
                        query("q2", List.of("case-b"), List.of(), List.of(), EvidenceStatus.INSUFFICIENT_EVIDENCE, false, true, false),
                        query("q3", List.of(), List.of("cat-c"), List.of("guide-c"), EvidenceStatus.AMBIGUOUS, false, false, true)
                )
        );
        List<EvaluationQueryResult> results = List.of(
                result("q1", EvidenceStatus.REFERENCE_SUPPORTED, true, true,
                        List.of("case-a", "case-x"), List.of("cat-a"), List.of("guide-x", "guide-a")),
                result("q2", EvidenceStatus.REFERENCE_SUPPORTED, false, false,
                        List.of("case-x"), List.of(), List.of()),
                result("q3", EvidenceStatus.AMBIGUOUS, true, false,
                        List.of(), List.of("cat-x", "cat-c"), List.of("guide-c"))
        );

        EvaluationReport report = calculator.calculate(dataset, true, results, 5, 3, 3);

        assertThat(report.verifiedCaseMetrics().recallAtK()).isCloseTo(0.5, offset(0.000001));
        assertThat(report.verifiedCaseMetrics().precisionAtK()).isCloseTo(0.25, offset(0.000001));
        assertThat(report.verifiedCaseMetrics().mrr()).isCloseTo(0.5, offset(0.000001));
        assertThat(report.categoryReferenceMetrics().recallAtK()).isCloseTo(1.0, offset(0.000001));
        assertThat(report.categoryReferenceMetrics().precisionAtK()).isCloseTo(0.75, offset(0.000001));
        assertThat(report.categoryReferenceMetrics().mrr()).isCloseTo(0.75, offset(0.000001));
        assertThat(report.officialGuideMetrics().mrr()).isCloseTo(0.75, offset(0.000001));
        assertThat(report.evidenceStatusAccuracy()).isCloseTo(2.0 / 3.0, offset(0.000001));
        assertThat(report.riskSignalPreservationRate()).isEqualTo(1.0);
        assertThat(report.noMatchRejectionAccuracy()).isEqualTo(1.0);
        assertThat(report.ambiguityDetectionAccuracy()).isEqualTo(1.0);
    }

    @Test
    void returnsNullForUnavailableOptionalMetrics() {
        EvaluationDataset dataset = new EvaluationDataset(
                "empty-optional",
                "synthetic metric test",
                true,
                "complaint-structure-v1",
                "embed-v1-large-1536",
                "provisional-v1",
                List.of(),
                List.of(query("q1", List.of(), List.of(), List.of(), EvidenceStatus.INSUFFICIENT_EVIDENCE, false, false, false))
        );
        List<EvaluationQueryResult> results = List.of(result(
                "q1",
                EvidenceStatus.INSUFFICIENT_EVIDENCE,
                true,
                false,
                List.of(),
                List.of(),
                List.of()
        ));

        EvaluationReport report = calculator.calculate(dataset, true, results, 5, 3, 3);

        assertThat(report.verifiedCaseMetrics().recallAtK()).isNull();
        assertThat(report.verifiedCaseMetrics().precisionAtK()).isNull();
        assertThat(report.verifiedCaseMetrics().mrr()).isNull();
        assertThat(report.riskSignalPreservationRate()).isNull();
        assertThat(report.noMatchRejectionAccuracy()).isNull();
        assertThat(report.ambiguityDetectionAccuracy()).isNull();
    }

    private EvaluationQueryCase query(
            String queryKey,
            List<String> cases,
            List<String> categories,
            List<String> guides,
            EvidenceStatus expectedStatus,
            boolean risk,
            boolean noMatch,
            boolean ambiguous
    ) {
        return new EvaluationQueryCase(
                queryKey,
                "description",
                InputSource.TEXT,
                "masked",
                complaint(),
                cases,
                categories,
                guides,
                expectedStatus,
                risk,
                noMatch,
                ambiguous
        );
    }

    private EvaluationQueryResult result(
            String queryKey,
            EvidenceStatus actualStatus,
            boolean statusMatched,
            boolean risk,
            List<String> cases,
            List<String> categories,
            List<String> guides
    ) {
        return new EvaluationQueryResult(
                queryKey,
                EvidenceStatus.INSUFFICIENT_EVIDENCE,
                actualStatus,
                statusMatched,
                risk,
                risk,
                cases,
                categories,
                guides
        );
    }

    private StructuredComplaint complaint() {
        return new StructuredComplaint(
                "summary",
                new ComplaintContext(PlaceType.UNKNOWN, RelationshipType.UNKNOWN, TimePattern.UNKNOWN),
                List.of(new ObservedFact(FactType.ACTION, "value", "evidence", Certainty.EXPLICIT)),
                List.of(),
                List.of(new SubjectMatter(SubjectMatterCode.GENERAL_CONSULTATION, "evidence", Certainty.EXPLICIT)),
                List.of(new RequestedAction(RequestedActionCode.CONSULT, "evidence", Certainty.EXPLICIT)),
                OngoingStatus.UNKNOWN,
                List.of()
        );
    }
}
