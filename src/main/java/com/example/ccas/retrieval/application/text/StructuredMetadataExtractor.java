package com.example.ccas.retrieval.application.text;

import com.example.ccas.retrieval.domain.RequestedAction;
import com.example.ccas.retrieval.domain.RiskSignal;
import com.example.ccas.retrieval.domain.StructuredComplaint;
import com.example.ccas.retrieval.domain.SubjectMatter;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class StructuredMetadataExtractor {

    public RetrievalMetadata extract(StructuredComplaint complaint) {
        Objects.requireNonNull(complaint, "complaint must not be null");

        return new RetrievalMetadata(
                distinctCodes(complaint.subjectMatters(), subjectMatter -> subjectMatter.code().name()),
                distinctCodes(complaint.requestedActions(), action -> action.code().name()),
                distinctCodes(complaint.riskSignals(), signal -> signal.code().name()),
                complaint.context().placeType().name(),
                complaint.context().relationshipType().name(),
                complaint.ongoingStatus().name()
        );
    }

    private <T> List<String> distinctCodes(List<T> values, Function<T, String> codeExtractor) {
        return values.stream()
                .map(codeExtractor)
                .distinct()
                .collect(Collectors.toList());
    }
}
