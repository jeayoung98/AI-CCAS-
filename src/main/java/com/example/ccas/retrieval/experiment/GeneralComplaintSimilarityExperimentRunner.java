package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.report.GeneralComplaintSimilarityReport;
import com.example.ccas.retrieval.experiment.report.GeneralComplaintSimilarityReportWriter;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
public class GeneralComplaintSimilarityExperimentRunner {

    private final GeneralComplaintSimilarityExperimentService service;
    private final GeneralComplaintSimilarityReportWriter reportWriter;

    public GeneralComplaintSimilarityExperimentRunner(
            GeneralComplaintSimilarityExperimentService service,
            GeneralComplaintSimilarityReportWriter reportWriter
    ) {
        this.service = service;
        this.reportWriter = reportWriter;
    }

    public Path run(ExperimentProperties properties) {
        GeneralComplaintSimilarityReport report = service.run(properties);
        return reportWriter.write(report, properties.variant());
    }
}
