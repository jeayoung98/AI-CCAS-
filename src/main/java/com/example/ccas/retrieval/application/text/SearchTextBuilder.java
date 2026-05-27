package com.example.ccas.retrieval.application.text;

import com.example.ccas.retrieval.domain.CategoryReference;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.OfficialGuideChunk;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.Certainty;
import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.RiskSignalCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
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

    public String buildForCategoryReference(CategoryReference reference) {
        Objects.requireNonNull(reference, "reference must not be null");

        return String.join("\n",
                "자료 유형: 민원 유형 기준",
                "민원 유형: " + reference.categoryName(),
                "설명: " + reference.description(),
                "포함 상황: " + String.join(", ", reference.inclusionCriteria()),
                "제외 상황: " + exclusionCriteriaText(reference),
                "관련 민원 대상: " + subjectMatterLabels(reference.subjectMatters()),
                "관련 요청 행위: " + requestedActionLabels(reference.supportedActions()),
                "주의할 위험 신호: " + relevantRiskSignalLabels(reference.relevantRiskSignals()),
                "공식 안내 근거 필요 여부: " + (reference.requiresOfficialGuide() ? "필요함" : "필요하지 않음")
        );
    }

    public String buildForOfficialGuide(OfficialGuideChunk guideChunk) {
        Objects.requireNonNull(guideChunk, "guideChunk must not be null");

        return String.join("\n",
                "자료 유형: 공식 안내 자료",
                "문서 제목: " + guideChunk.documentTitle(),
                "문서 구간: " + guideChunk.sectionTitle(),
                "대상 민원: " + subjectMatterLabels(guideChunk.subjectMatters()),
                "관련 요청 행위: " + relatedActionLabels(guideChunk.relatedActions()),
                "관련 위험 신호: " + relatedRiskSignalLabels(guideChunk.relatedRiskSignals()),
                "안내 내용: " + guideChunk.chunkText()
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

    private String exclusionCriteriaText(CategoryReference reference) {
        if (reference.exclusionCriteria().isEmpty()) {
            return "별도 제외 기준 없음";
        }
        return String.join(", ", reference.exclusionCriteria());
    }

    private String subjectMatterLabels(List<SubjectMatterCode> subjectMatters) {
        return distinctLabels(subjectMatters, SubjectMatterCode::searchLabel);
    }

    private String requestedActionLabels(List<RequestedActionCode> requestedActions) {
        return distinctLabels(requestedActions, RequestedActionCode::searchLabel);
    }

    private String relevantRiskSignalLabels(List<RiskSignalCode> riskSignals) {
        if (riskSignals.isEmpty()) {
            return "별도 위험 신호 없음";
        }
        return distinctLabels(riskSignals, RiskSignalCode::searchLabel);
    }

    private String relatedActionLabels(List<RequestedActionCode> requestedActions) {
        if (requestedActions.isEmpty()) {
            return "별도 요청 행위 없음";
        }
        return distinctLabels(requestedActions, RequestedActionCode::searchLabel);
    }

    private String relatedRiskSignalLabels(List<RiskSignalCode> riskSignals) {
        if (riskSignals.isEmpty()) {
            return "별도 위험 신호 없음";
        }
        return distinctLabels(riskSignals, RiskSignalCode::searchLabel);
    }

    private <T> String distinctLabels(List<T> values, Function<T, String> labelExtractor) {
        return values.stream()
                .distinct()
                .map(labelExtractor)
                .collect(Collectors.joining(", "));
    }

    private String appendUncertain(String value, Certainty certainty) {
        if (certainty == Certainty.UNCERTAIN) {
            return value + UNCERTAIN_SUFFIX;
        }
        return value;
    }
}
