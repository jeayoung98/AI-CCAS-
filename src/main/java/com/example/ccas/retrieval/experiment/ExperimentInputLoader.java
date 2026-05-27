package com.example.ccas.retrieval.experiment;

import com.example.ccas.retrieval.experiment.dto.ExperimentComplaintRecord;
import com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityPairs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class ExperimentInputLoader {

    private final ObjectMapper objectMapper;

    public ExperimentInputLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<ExperimentComplaintRecord> loadComplaintRecords(Path path) {
        ensureFileExists(path);
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<List<ExperimentComplaintRecord>>() {
            });
        } catch (IOException exception) {
            throw new ExperimentException("Failed to parse experiment input JSON: " + path, exception);
        }
    }

    public ExpectedSimilarityPairs loadExpectedPairs(Path path) {
        ensureFileExists(path);
        try {
            return objectMapper.readValue(path.toFile(), ExpectedSimilarityPairs.class);
        } catch (IOException exception) {
            throw new ExperimentException("Failed to parse expected pairs JSON: " + path, exception);
        }
    }

    private void ensureFileExists(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new ExperimentException("Experiment input file does not exist: " + path);
        }
    }
}
