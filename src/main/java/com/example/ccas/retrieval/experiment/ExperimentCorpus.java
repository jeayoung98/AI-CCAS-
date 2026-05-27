package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintRecord;
import com.example.ccas.retrieval.experiment.report.ExcludedExperimentRecord;

import java.util.List;

public record ExperimentCorpus(
        List<ExperimentComplaintRecord> records,
        List<ExcludedExperimentRecord> excludedRecords
) {
}
