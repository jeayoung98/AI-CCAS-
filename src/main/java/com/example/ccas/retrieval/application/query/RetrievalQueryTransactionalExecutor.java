package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchService;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalHitRepository;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalQueryInsertRow;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalQueryTransactionalExecutor {

    private final RetrievalQueryRepository retrievalQueryRepository;
    private final RetrievalSearchService retrievalSearchService;
    private final RetrievalHitRepository retrievalHitRepository;

    public RetrievalQueryTransactionalExecutor(
            RetrievalQueryRepository retrievalQueryRepository,
            RetrievalSearchService retrievalSearchService,
            RetrievalHitRepository retrievalHitRepository
    ) {
        this.retrievalQueryRepository = retrievalQueryRepository;
        this.retrievalSearchService = retrievalSearchService;
        this.retrievalHitRepository = retrievalHitRepository;
    }

    @Transactional
    public RetrievalCandidates persistAndSearch(RetrievalQueryInsertRow row, EmbeddingResult queryEmbedding) {
        retrievalQueryRepository.save(row);
        RetrievalCandidates candidates = retrievalSearchService.retrieveCandidates(queryEmbedding);
        retrievalHitRepository.saveAll(row.id(), candidates);
        return candidates;
    }
}
