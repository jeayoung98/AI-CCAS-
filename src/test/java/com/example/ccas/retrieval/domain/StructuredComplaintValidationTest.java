package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.FactType;
import com.example.ccas.retrieval.domain.type.OngoingStatus;
import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import com.example.ccas.retrieval.domain.type.TimePattern;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredComplaintValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidStructuredComplaintContractValues() {
        assertInvalid(baseComplaint(" ", facts(), risks(), subjects(), actions(), OngoingStatus.PAST_EVENT),
                "factualSummary");
        assertInvalid(baseComplaint("요약", List.of(), risks(), subjects(), actions(), OngoingStatus.PAST_EVENT),
                "observedFacts");
        assertInvalid(baseComplaint("요약", facts(), risks(), List.of(), actions(), OngoingStatus.PAST_EVENT),
                "subjectMatters");
        assertInvalid(baseComplaint("요약", facts(), risks(), subjects(), List.of(), OngoingStatus.PAST_EVENT),
                "requestedActions");
        assertInvalid(baseComplaint("요약", facts(), risks(), subjects(), actions(), null),
                "ongoingStatus");
        assertInvalid(baseComplaint("요약", List.of(new ObservedFact(FactType.ACTION, " ", "증거", Certainty.EXPLICIT)),
                        risks(), subjects(), actions(), OngoingStatus.PAST_EVENT),
                "observedFacts[0].value");
        assertInvalid(baseComplaint("요약", facts(), List.of(new RiskSignal(RiskSignalCode.WEAPON, " ", Certainty.EXPLICIT)),
                        subjects(), actions(), OngoingStatus.PAST_EVENT),
                "riskSignals[0].evidence");
    }

    private void assertInvalid(StructuredComplaint complaint, String propertyPath) {
        Set<ConstraintViolation<StructuredComplaint>> violations = validator.validate(complaint);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyPath);
    }

    private StructuredComplaint baseComplaint(
            String factualSummary,
            List<ObservedFact> observedFacts,
            List<RiskSignal> riskSignals,
            List<SubjectMatter> subjectMatters,
            List<RequestedAction> requestedActions,
            OngoingStatus ongoingStatus
    ) {
        return new StructuredComplaint(
                factualSummary,
                new ComplaintContext(PlaceType.PUBLIC_FACILITY, RelationshipType.UNKNOWN, TimePattern.ONE_TIME),
                observedFacts,
                riskSignals,
                subjectMatters,
                requestedActions,
                ongoingStatus,
                List.of()
        );
    }

    private List<ObservedFact> facts() {
        return List.of(new ObservedFact(FactType.ACTION, "지갑 분실", "지갑을 잃어버렸다고 말함", Certainty.EXPLICIT));
    }

    private List<RiskSignal> risks() {
        return List.of();
    }

    private List<SubjectMatter> subjects() {
        return List.of(new SubjectMatter(SubjectMatterCode.LOST_ITEM, "지갑 분실", Certainty.EXPLICIT));
    }

    private List<RequestedAction> actions() {
        return List.of(new RequestedAction(RequestedActionCode.SEARCH, "찾는 방법 문의", Certainty.EXPLICIT));
    }
}
