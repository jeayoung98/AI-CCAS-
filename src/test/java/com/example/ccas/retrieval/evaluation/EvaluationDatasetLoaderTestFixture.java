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

import java.util.List;

class EvaluationDatasetLoaderTestFixture {

    EvaluationQueryCase query() {
        return new EvaluationQueryCase(
                "q",
                "query",
                InputSource.TEXT,
                "masked",
                new StructuredComplaint(
                        "summary",
                        new ComplaintContext(PlaceType.UNKNOWN, RelationshipType.UNKNOWN, TimePattern.UNKNOWN),
                        List.of(new ObservedFact(FactType.ACTION, "value", "evidence", Certainty.EXPLICIT)),
                        List.of(),
                        List.of(new SubjectMatter(SubjectMatterCode.GENERAL_CONSULTATION, "evidence", Certainty.EXPLICIT)),
                        List.of(new RequestedAction(RequestedActionCode.CONSULT, "evidence", Certainty.EXPLICIT)),
                        OngoingStatus.UNKNOWN,
                        List.of()
                ),
                List.of(),
                List.of(),
                List.of(),
                EvidenceStatus.INSUFFICIENT_EVIDENCE,
                false,
                false,
                false
        );
    }
}
