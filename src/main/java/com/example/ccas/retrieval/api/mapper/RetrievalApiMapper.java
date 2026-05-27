package com.example.ccas.retrieval.api.mapper;

import com.example.ccas.retrieval.api.dto.ComplaintContextRequest;
import com.example.ccas.retrieval.api.dto.EvidenceDecisionResponse;
import com.example.ccas.retrieval.api.dto.ObservedFactRequest;
import com.example.ccas.retrieval.api.dto.RequestedActionRequest;
import com.example.ccas.retrieval.api.dto.RetrievalChannelsResponse;
import com.example.ccas.retrieval.api.dto.RetrievalHitResponse;
import com.example.ccas.retrieval.api.dto.RetrievalRequest;
import com.example.ccas.retrieval.api.dto.RetrievalResponse;
import com.example.ccas.retrieval.api.dto.RiskSignalRequest;
import com.example.ccas.retrieval.api.dto.ScoreSummaryResponse;
import com.example.ccas.retrieval.api.dto.StructuredComplaintRequest;
import com.example.ccas.retrieval.api.dto.SubjectMatterRequest;
import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceDecision;
import com.example.ccas.retrieval.application.evaluation.RetrievalHitAssessment;
import com.example.ccas.retrieval.application.query.ExecuteRetrievalQueryCommand;
import com.example.ccas.retrieval.application.query.RetrievalQueryExecutionResult;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.domain.ComplaintContext;
import com.example.ccas.retrieval.domain.ObservedFact;
import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class RetrievalApiMapper {

    public ExecuteRetrievalQueryCommand toCommand(RetrievalRequest request) {
        return new ExecuteRetrievalQueryCommand(
                request.inputSource(),
                request.maskingCompleted(),
                request.maskedText(),
                toStructuredComplaint(request.structuredComplaint())
        );
    }

    public RetrievalResponse toResponse(RetrievalQueryExecutionResult result) {
        RetrievalEvidenceDecision decision = result.decision();
        return new RetrievalResponse(
                result.queryId(),
                result.riskSignalPresent(),
                toDecisionResponse(decision),
                toChannelsResponse(result.candidates(), decision.hitAssessments())
        );
    }

    private StructuredComplaint toStructuredComplaint(StructuredComplaintRequest request) {
        return new StructuredComplaint(
                request.factualSummary(),
                toContext(request.context()),
                request.observedFacts().stream().map(this::toObservedFact).toList(),
                request.riskSignals().stream().map(this::toRiskSignal).toList(),
                request.subjectMatters().stream().map(this::toSubjectMatter).toList(),
                request.requestedActions().stream().map(this::toRequestedAction).toList(),
                request.ongoingStatus(),
                request.missingInformation()
        );
    }

    private ComplaintContext toContext(ComplaintContextRequest request) {
        return new ComplaintContext(request.placeType(), request.relationshipType(), request.timePattern());
    }

    private ObservedFact toObservedFact(ObservedFactRequest request) {
        return new ObservedFact(request.factType(), request.value(), request.evidence(), request.certainty());
    }

    private RiskSignal toRiskSignal(RiskSignalRequest request) {
        return new RiskSignal(request.code(), request.evidence(), request.certainty());
    }

    private SubjectMatter toSubjectMatter(SubjectMatterRequest request) {
        return new SubjectMatter(request.code(), request.evidence(), request.certainty());
    }

    private RequestedAction toRequestedAction(RequestedActionRequest request) {
        return new RequestedAction(request.code(), request.evidence(), request.certainty());
    }

    private EvidenceDecisionResponse toDecisionResponse(RetrievalEvidenceDecision decision) {
        return new EvidenceDecisionResponse(
                decision.evidenceStatus(),
                decision.provisionalThreshold(),
                decision.thresholdProfileVersion(),
                new ScoreSummaryResponse(
                        decision.bestCaseScore(),
                        decision.bestCategoryScore(),
                        decision.bestGuideScore(),
                        decision.categoryMargin(),
                        decision.caseCategoryAgreement()
                ),
                decision.decisionReasonCodes()
        );
    }

    private RetrievalChannelsResponse toChannelsResponse(
            RetrievalCandidates candidates,
            List<RetrievalHitAssessment> assessments
    ) {
        Map<AssessmentKey, RetrievalHitAssessment> assessmentByHit = assessments.stream()
                .collect(Collectors.toMap(
                        assessment -> new AssessmentKey(assessment.channel(), assessment.itemId(), assessment.rank()),
                        Function.identity()
                ));
        return new RetrievalChannelsResponse(
                toHitResponses(candidates.verifiedCases(), assessmentByHit),
                toHitResponses(candidates.categoryReferences(), assessmentByHit),
                toHitResponses(candidates.officialGuides(), assessmentByHit)
        );
    }

    private List<RetrievalHitResponse> toHitResponses(
            List<RetrievalSearchHit> hits,
            Map<AssessmentKey, RetrievalHitAssessment> assessmentByHit
    ) {
        return hits.stream()
                .map(hit -> toHitResponse(hit, assessmentByHit))
                .toList();
    }

    private RetrievalHitResponse toHitResponse(
            RetrievalSearchHit hit,
            Map<AssessmentKey, RetrievalHitAssessment> assessmentByHit
    ) {
        RetrievalHitAssessment assessment = assessmentByHit.get(new AssessmentKey(hit.itemType(), hit.itemId(), hit.rank()));
        if (assessment == null) {
            throw new IllegalStateException("Missing retrieval hit assessment.");
        }
        return new RetrievalHitResponse(
                hit.itemId(),
                hit.itemType(),
                hit.title(),
                hit.structuredPayload(),
                hit.subjectMatterCodes(),
                hit.requestedActionCodes(),
                hit.riskSignalCodes(),
                hit.verifiedCategoryCode(),
                hit.verifiedRouteCode(),
                hit.cosineSimilarity(),
                hit.rank(),
                assessment.passedChannelCutoff(),
                assessment.rejectionReason()
        );
    }

    private record AssessmentKey(RetrievalItemType itemType, long itemId, int rank) {
    }
}
