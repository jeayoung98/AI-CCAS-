package com.example.ccas.retrieval.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EvaluationDatasetLoader {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public EvaluationDatasetLoader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public EvaluationDataset load(Path path) {
        try {
            EvaluationDataset dataset = objectMapper.readValue(path.toFile(), EvaluationDataset.class);
            validate(dataset);
            return dataset;
        } catch (IOException exception) {
            throw new EvaluationDatasetException("Failed to load retrieval evaluation dataset.", exception);
        }
    }

    public EvaluationDataset loadSynthetic(Path path) {
        EvaluationDataset dataset = load(path);
        if (!dataset.synthetic()) {
            throw new EvaluationDatasetException("Synthetic evaluation path must contain synthetic=true dataset.");
        }
        return dataset;
    }

    private void validate(EvaluationDataset dataset) {
        Set<ConstraintViolation<EvaluationDataset>> violations = validator.validate(dataset);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new EvaluationDatasetException(message);
        }
    }
}
