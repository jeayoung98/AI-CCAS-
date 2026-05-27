package com.example.ccas.retrieval.experiment.report;

import com.example.ccas.retrieval.experiment.ExperimentException;
import com.example.ccas.retrieval.experiment.ExperimentSearchTextVariant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class GeneralComplaintSimilarityReportWriter {

    private final ObjectMapper objectMapper;

    public GeneralComplaintSimilarityReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path write(GeneralComplaintSimilarityReport report, ExperimentSearchTextVariant variant) {
        Path reportDirectory = Path.of("build", "reports", "general-complaint-similarity");
        Path reportPath = reportDirectory.resolve(variant.reportName() + "-result.json");
        try {
            Files.createDirectories(reportDirectory);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
            return reportPath;
        } catch (IOException exception) {
            throw new ExperimentException("Failed to write semantic similarity experiment report.", exception);
        }
    }
}
