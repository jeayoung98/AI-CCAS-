package com.example.ccas.retrieval.application.evaluation;

import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchHit;
import com.example.ccas.retrieval.config.RetrievalEvaluationProperties;
import com.example.ccas.retrieval.domain.type.DecisionReasonCode;
import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import com.example.ccas.retrieval.domain.type.HitRejectionReason;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class RetrievalEvidenceEvaluator {

    private final RetrievalEvaluationProperties properties;

    public RetrievalEvidenceEvaluator(RetrievalEvaluationProperties properties) {
        this.properties = properties;
    }

    public RetrievalEvidenceDecision evaluate(RetrievalCandidates candidates, boolean riskSignalPresent) {
        List<RetrievalSearchHit> acceptedCases = acceptedHits(
                candidates.verifiedCases(),
                properties.caseMinSimilarity()
        );
        List<RetrievalSearchHit> acceptedCategoryReferences = acceptedHits(
                candidates.categoryReferences(),
                properties.categoryMinSimilarity()
        );
        List<RetrievalSearchHit> acceptedOfficialGuides = acceptedHits(
                candidates.officialGuides(),
                properties.guideMinSimilarity()
        );

        List<RetrievalSearchHit> reliableCases = acceptedCases.stream()
                .filter(hit -> hasText(hit.verifiedCategoryCode()))
                .toList();
        List<RetrievalSearchHit> reliableCategoryReferences = acceptedCategoryReferences.stream()
                .filter(hit -> hasText(hit.verifiedCategoryCode()))
                .toList();

        Double bestCaseScore = bestScore(candidates.verifiedCases());
        Double bestCategoryScore = bestScore(candidates.categoryReferences());
        Double bestGuideScore = bestScore(candidates.officialGuides());
        Double categoryMargin = categoryMargin(acceptedCategoryReferences);
        boolean categoryMarginConflict = categoryMargin != null
                && categoryMargin < properties.categoryMarginMin();
        Double caseCategoryAgreement = caseCategoryAgreement(reliableCases);
        boolean caseCategoryConflict = reliableCases.size() >= 2
                && caseCategoryAgreement != null
                && caseCategoryAgreement < properties.caseAgreementMin();

        String dominantCaseCategory = dominantCaseCategory(reliableCases);
        String topCategoryReferenceCode = topCategoryReferenceCode(reliableCategoryReferences);
        boolean caseReferenceMismatch = hasText(dominantCaseCategory)
                && hasText(topCategoryReferenceCode)
                && !Objects.equals(dominantCaseCategory, topCategoryReferenceCode);

        Set<DecisionReasonCode> reasons = new LinkedHashSet<>();
        addSupportReasons(
                reasons,
                !reliableCases.isEmpty(),
                !reliableCategoryReferences.isEmpty(),
                !acceptedOfficialGuides.isEmpty()
        );
        if (categoryMarginConflict) {
            reasons.add(DecisionReasonCode.CATEGORY_MARGIN_TOO_SMALL);
        }
        if (caseCategoryConflict) {
            reasons.add(DecisionReasonCode.CASE_CATEGORY_CONFLICT);
        }
        if (caseReferenceMismatch) {
            reasons.add(DecisionReasonCode.CASE_REFERENCE_CATEGORY_MISMATCH);
        }
        if (riskSignalPresent) {
            reasons.add(DecisionReasonCode.RISK_SIGNAL_PRESENT);
        }

        EvidenceStatus status = decideStatus(
                !reliableCases.isEmpty(),
                !reliableCategoryReferences.isEmpty(),
                !acceptedOfficialGuides.isEmpty(),
                categoryMarginConflict,
                caseCategoryConflict,
                caseReferenceMismatch
        );

        return new RetrievalEvidenceDecision(
                status,
                riskSignalPresent,
                bestCaseScore,
                bestCategoryScore,
                bestGuideScore,
                categoryMargin,
                caseCategoryAgreement,
                new ArrayList<>(reasons),
                hitAssessments(candidates),
                properties.thresholdProfileVersion(),
                properties.provisional()
        );
    }

    private List<RetrievalSearchHit> acceptedHits(List<RetrievalSearchHit> hits, double threshold) {
        return hits.stream()
                .filter(hit -> hit.cosineSimilarity() >= threshold)
                .toList();
    }

    private Double bestScore(List<RetrievalSearchHit> hits) {
        return hits.stream()
                .map(RetrievalSearchHit::cosineSimilarity)
                .max(Double::compareTo)
                .orElse(null);
    }

    private Double categoryMargin(List<RetrievalSearchHit> acceptedCategoryReferences) {
        if (acceptedCategoryReferences.size() < 2) {
            return null;
        }
        List<RetrievalSearchHit> sorted = acceptedCategoryReferences.stream()
                .sorted(Comparator.comparingInt(RetrievalSearchHit::rank))
                .toList();
        return sorted.get(0).cosineSimilarity() - sorted.get(1).cosineSimilarity();
    }

    private Double caseCategoryAgreement(List<RetrievalSearchHit> reliableCases) {
        if (reliableCases.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = categoryCounts(reliableCases);
        int maxCount = counts.values().stream().max(Integer::compareTo).orElse(0);
        return maxCount / (double) reliableCases.size();
    }

    private String dominantCaseCategory(List<RetrievalSearchHit> reliableCases) {
        if (reliableCases.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = categoryCounts(reliableCases);
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private Map<String, Integer> categoryCounts(List<RetrievalSearchHit> hits) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (RetrievalSearchHit hit : hits) {
            counts.merge(hit.verifiedCategoryCode(), 1, Integer::sum);
        }
        return counts;
    }

    private String topCategoryReferenceCode(List<RetrievalSearchHit> reliableCategoryReferences) {
        return reliableCategoryReferences.stream()
                .min(Comparator.comparingInt(RetrievalSearchHit::rank))
                .map(RetrievalSearchHit::verifiedCategoryCode)
                .orElse(null);
    }

    private void addSupportReasons(
            Set<DecisionReasonCode> reasons,
            boolean reliableCaseFound,
            boolean reliableCategoryFound,
            boolean reliableGuideFound
    ) {
        reasons.add(reliableCaseFound
                ? DecisionReasonCode.RELIABLE_VERIFIED_CASE_FOUND
                : DecisionReasonCode.NO_RELIABLE_VERIFIED_CASE);
        reasons.add(reliableCategoryFound
                ? DecisionReasonCode.RELIABLE_CATEGORY_REFERENCE_FOUND
                : DecisionReasonCode.NO_RELIABLE_CATEGORY_REFERENCE);
        reasons.add(reliableGuideFound
                ? DecisionReasonCode.RELIABLE_OFFICIAL_GUIDE_FOUND
                : DecisionReasonCode.NO_RELIABLE_OFFICIAL_GUIDE);
    }

    private EvidenceStatus decideStatus(
            boolean reliableCaseFound,
            boolean reliableCategoryFound,
            boolean reliableGuideFound,
            boolean categoryMarginConflict,
            boolean caseCategoryConflict,
            boolean caseReferenceMismatch
    ) {
        if (categoryMarginConflict || caseCategoryConflict || caseReferenceMismatch) {
            return EvidenceStatus.AMBIGUOUS;
        }
        if (reliableCaseFound && reliableCategoryFound && reliableGuideFound) {
            return EvidenceStatus.CASE_AND_REFERENCE_SUPPORTED;
        }
        if (reliableCategoryFound && reliableGuideFound) {
            return EvidenceStatus.REFERENCE_SUPPORTED;
        }
        if (reliableGuideFound && !reliableCaseFound && !reliableCategoryFound) {
            return EvidenceStatus.GUIDE_ONLY;
        }
        return EvidenceStatus.INSUFFICIENT_EVIDENCE;
    }

    private List<RetrievalHitAssessment> hitAssessments(RetrievalCandidates candidates) {
        return Stream.of(
                        hitAssessments(candidates.verifiedCases(), properties.caseMinSimilarity(), HitRejectionReason.BELOW_CASE_MIN_SIMILARITY),
                        hitAssessments(candidates.categoryReferences(), properties.categoryMinSimilarity(), HitRejectionReason.BELOW_CATEGORY_MIN_SIMILARITY),
                        hitAssessments(candidates.officialGuides(), properties.guideMinSimilarity(), HitRejectionReason.BELOW_GUIDE_MIN_SIMILARITY)
                )
                .flatMap(List::stream)
                .toList();
    }

    private List<RetrievalHitAssessment> hitAssessments(
            List<RetrievalSearchHit> hits,
            double threshold,
            HitRejectionReason rejectionReason
    ) {
        return hits.stream()
                .map(hit -> {
                    boolean passed = hit.cosineSimilarity() >= threshold;
                    return new RetrievalHitAssessment(
                            hit.itemType(),
                            hit.itemId(),
                            hit.rank(),
                            passed,
                            passed ? null : rejectionReason
                    );
                })
                .toList();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
