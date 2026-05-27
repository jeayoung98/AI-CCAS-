package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.infrastructure.persistence.KnowledgeSourceRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class KnowledgeSourceIngestionService {

    private final KnowledgeSourceRepository repository;
    private final Validator validator;

    public KnowledgeSourceIngestionService(KnowledgeSourceRepository repository, Validator validator) {
        this.repository = repository;
        this.validator = validator;
    }

    @Transactional
    public long createSource(CreateKnowledgeSourceCommand command) {
        validate(command);
        return repository.save(command);
    }

    private void validate(CreateKnowledgeSourceCommand command) {
        Set<ConstraintViolation<CreateKnowledgeSourceCommand>> violations = validator.validate(command);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
