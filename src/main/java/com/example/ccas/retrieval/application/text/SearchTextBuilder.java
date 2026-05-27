package com.example.ccas.retrieval.application.text;

import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;

import java.util.Objects;
import java.util.stream.Collectors;

public class SearchTextBuilder {

    private static final String NO_EXPLICIT_RISK_SIGNAL = "명시적 위험 신호 없음";
    private static final String UNCERTAIN_SUFFIX = "(확인 필요)";

    public String buildForComplaint(StructuredComplaint complaint) {
        Objects.requireNonNull(complaint, "complaint must not be null");

        return String.join("\n",
                "발생 상황: " + complaint.factualSummary(),
                "장소 맥락: " + complaint.context().placeType().searchLabel(),
                "관계 맥락: " + complaint.context().relationshipType().searchLabel(),
                "발생 패턴: " + complaint.context().timePattern().searchLabel(),
                "관찰된 사실: " + observedFactsText(complaint),
                "위험 신호: " + riskSignalsText(complaint),
                "민원 대상: " + subjectMattersText(complaint),
                "요청 행위: " + requestedActionsText(complaint),
                "현재성: " + complaint.ongoingStatus().searchLabel()
        );
    }

    private String observedFactsText(StructuredComplaint complaint) {
        return complaint.observedFacts().stream()
                .map(fact -> appendUncertain(fact.value(), fact.certainty()))
                .collect(Collectors.joining(", "));
    }

    private String riskSignalsText(StructuredComplaint complaint) {
        if (complaint.riskSignals().isEmpty()) {
            return NO_EXPLICIT_RISK_SIGNAL;
        }

        return complaint.riskSignals().stream()
                .map(signal -> appendUncertain(signal.code().searchLabel(), signal.certainty()))
                .collect(Collectors.joining(", "));
    }

    private String subjectMattersText(StructuredComplaint complaint) {
        return complaint.subjectMatters().stream()
                .map(subjectMatter -> appendUncertain(subjectMatter.code().searchLabel(), subjectMatter.certainty()))
                .collect(Collectors.joining(", "));
    }

    private String requestedActionsText(StructuredComplaint complaint) {
        return complaint.requestedActions().stream()
                .map(action -> appendUncertain(action.code().searchLabel(), action.certainty()))
                .collect(Collectors.joining(", "));
    }

    private String appendUncertain(String value, Certainty certainty) {
        if (certainty == Certainty.UNCERTAIN) {
            return value + UNCERTAIN_SUFFIX;
        }
        return value;
    }
}
