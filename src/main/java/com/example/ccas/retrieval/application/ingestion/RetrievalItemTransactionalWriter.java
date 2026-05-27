package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.infrastructure.persistence.RetrievalItemInsertRow;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RetrievalItemTransactionalWriter {

    private final RetrievalItemRepository repository;

    RetrievalItemTransactionalWriter(RetrievalItemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    long save(RetrievalItemInsertRow row) {
        return repository.save(row);
    }
}
