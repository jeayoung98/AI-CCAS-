package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintRecord;
import com.example.ccas.retrieval.experiment.dto.ExperimentObservedFact;
import com.example.ccas.retrieval.experiment.report.ExcludedExperimentRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ExperimentCorpusSelector {

    public ExperimentCorpus select(List<ExperimentComplaintRecord> records) {
        if (records == null) {
            throw new ExperimentException("output.json must contain an array of complaint records.");
        }
        List<ExperimentComplaintRecord> included = new ArrayList<>();
        List<ExcludedExperimentRecord> excluded = new ArrayList<>();
        Set<Long> includedIds = new HashSet<>();

        for (ExperimentComplaintRecord record : records) {
            List<String> reasons = exclusionReasons(record);
            if (reasons.isEmpty()) {
                if (!includedIds.add(record.id())) {
                    throw new ExperimentException("Duplicate corpus complaint id: " + record.id());
                }
                included.add(record);
            } else {
                excluded.add(new ExcludedExperimentRecord(record == null ? 0L : record.id(), reasons));
            }
        }
        return new ExperimentCorpus(List.copyOf(included), List.copyOf(excluded));
    }

    private List<String> exclusionReasons(ExperimentComplaintRecord record) {
        List<String> reasons = new ArrayList<>();
        if (record == null) {
            reasons.add("STRUCTURED_NULL");
            return reasons;
        }
        if (!record.ok()) {
            reasons.add("SOURCE_MARKED_FAILED");
        }
        if (isBlank(record.title())) {
            reasons.add("TITLE_BLANK");
        }
        if (record.structured() == null) {
            reasons.add("STRUCTURED_NULL");
            return reasons;
        }
        if (isBlank(record.structured().factualSummary())) {
            reasons.add("FACTUAL_SUMMARY_BLANK");
        }
        if (record.structured().context() == null) {
            reasons.add("CONTEXT_NULL");
        }
        if (record.structured().observedFacts() == null || record.structured().observedFacts().isEmpty()) {
            reasons.add("OBSERVED_FACTS_EMPTY");
        } else if (record.structured().observedFacts().stream()
                .map(ExperimentObservedFact::value)
                .anyMatch(this::isBlank)) {
            reasons.add("OBSERVED_FACT_VALUE_BLANK");
        }
        return reasons;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
