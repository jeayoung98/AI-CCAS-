package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceDecision;
import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceEvaluator;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;
import com.example.ccas.retrieval.application.search.RetrievalSearchService;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalDecisionRepository;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalHitRepository;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalQueryInsertRow;
import com.example.ccas.retrieval.infrastructure.persistence.RetrievalQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RetrievalQueryTransactionalExecutor {

    private final RetrievalQueryRepository retrievalQueryRepository;
    private final RetrievalSearchService retrievalSearchService;
    private final RetrievalEvidenceEvaluator evidenceEvaluator;
    private final RetrievalHitRepository retrievalHitRepository;
    private final RetrievalDecisionRepository retrievalDecisionRepository;

    public RetrievalQueryTransactionalExecutor(
            RetrievalQueryRepository retrievalQueryRepository,
            RetrievalSearchService retrievalSearchService,
            RetrievalEvidenceEvaluator evidenceEvaluator,
            RetrievalHitRepository retrievalHitRepository,
            RetrievalDecisionRepository retrievalDecisionRepository
    ) {
        this.retrievalQueryRepository = retrievalQueryRepository;
        this.retrievalSearchService = retrievalSearchService;
        this.evidenceEvaluator = evidenceEvaluator;
        this.retrievalHitRepository = retrievalHitRepository;
        this.retrievalDecisionRepository = retrievalDecisionRepository;
    }

    @Transactional
    public RetrievalQueryTransactionResult persistSearchAndEvaluate(
            RetrievalQueryInsertRow row,
            EmbeddingResult queryEmbedding,
            boolean riskSignalPresent
    ) {
        retrievalQueryRepository.save(row);
        RetrievalCandidates candidates = retrievalSearchService.retrieveCandidates(queryEmbedding);
        RetrievalEvidenceDecision decision = evidenceEvaluator.evaluate(candidates, riskSignalPresent);
        retrievalHitRepository.saveAll(row.id(), candidates, decision.hitAssessments());
        retrievalDecisionRepository.save(row.id(), decision);
        return new RetrievalQueryTransactionResult(candidates, decision);
    }
}
