package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintRecord;
import com.example.ccas.retrieval.experiment.dto.ExperimentObservedFact;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class GeneralComplaintExperimentSearchTextBuilder {

    public String build(ExperimentComplaintRecord record, ExperimentSearchTextVariant variant) {
        String common = """
                민원 요약: %s
                핵심 사실: %s
                발생 장소 유형: %s
                발생 패턴: %s
                """.formatted(
                record.structured().factualSummary(),
                observedFacts(record),
                record.structured().context().placeType(),
                record.structured().context().timePattern()
        ).stripTrailing();

        if (variant == ExperimentSearchTextVariant.WITH_TITLE) {
            return """
                    제목: %s
                    %s
                    """.formatted(record.title(), common).stripTrailing();
        }
        return common;
    }

    private String observedFacts(ExperimentComplaintRecord record) {
        return record.structured().observedFacts().stream()
                .map(ExperimentObservedFact::value)
                .collect(Collectors.joining(", "));
    }
}
