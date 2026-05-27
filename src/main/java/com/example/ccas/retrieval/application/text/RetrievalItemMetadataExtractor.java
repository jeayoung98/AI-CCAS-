package com.example.ccas.retrieval.application.text;

import com.example.ccas.retrieval.domain.CategoryReference;
import com.example.ccas.retrieval.domain.OfficialGuideChunk;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RetrievalItemMetadataExtractor {

    public RetrievalMetadata extractFromCategoryReference(CategoryReference reference) {
        Objects.requireNonNull(reference, "reference must not be null");

        return new RetrievalMetadata(
                distinctCodes(reference.subjectMatters(), subjectMatter -> subjectMatter.name()),
                distinctCodes(reference.supportedActions(), action -> action.name()),
                distinctCodes(reference.relevantRiskSignals(), riskSignal -> riskSignal.name()),
                null,
                null,
                null
        );
    }

    public RetrievalMetadata extractFromOfficialGuide(OfficialGuideChunk guideChunk) {
        Objects.requireNonNull(guideChunk, "guideChunk must not be null");

        return new RetrievalMetadata(
                distinctCodes(guideChunk.subjectMatters(), subjectMatter -> subjectMatter.name()),
                distinctCodes(guideChunk.relatedActions(), action -> action.name()),
                distinctCodes(guideChunk.relatedRiskSignals(), riskSignal -> riskSignal.name()),
                null,
                null,
                null
        );
    }

    private <T> List<String> distinctCodes(List<T> values, Function<T, String> codeExtractor) {
        return values.stream()
                .map(codeExtractor)
                .distinct()
                .collect(Collectors.toList());
    }
}
