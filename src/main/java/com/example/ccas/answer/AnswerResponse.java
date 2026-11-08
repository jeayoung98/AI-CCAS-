package com.example.ccas.answer;

import com.example.ccas.retrieval.domain.type.EvidenceStatus;
import java.util.List;
import java.util.UUID;

public record AnswerResponse(
        UUID queryId, EvidenceStatus evidenceStatus, boolean riskSignalPresent,
        Status answerStatus, String answer, List<Citation> citations
) {
    public enum Status { GENERATED, ABSTAINED }

    public record Citation(long itemId, String documentTitle, String sectionTitle, String quote) {}
}
