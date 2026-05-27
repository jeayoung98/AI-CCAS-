package com.example.ccas.retrieval.experiment.report;

import java.util.List;

public record ExcludedExperimentRecord(
        long id,
        List<String> reasons
) {
}
