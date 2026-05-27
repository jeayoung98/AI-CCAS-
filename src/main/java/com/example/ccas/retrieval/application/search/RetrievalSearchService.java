package com.example.ccas.retrieval.application.search;

import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.PgVectorRetrievalSearchRepository;
import org.springframework.stereotype.Service;

@Service
public class RetrievalSearchService {

    private final PgVectorRetrievalSearchRepository repository;
    private final RetrievalSearchProperties properties;

    public RetrievalSearchService(
            PgVectorRetrievalSearchRepository repository,
            RetrievalSearchProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    public RetrievalCandidates retrieveCandidates(EmbeddingResult queryEmbedding) {
        validateQueryEmbedding(queryEmbedding);

        return new RetrievalCandidates(
                repository.searchByItemType(
                        RetrievalItemType.VERIFIED_CASE,
                        queryEmbedding,
                        properties.topK().verifiedCase()
                ),
                repository.searchByItemType(
                        RetrievalItemType.CATEGORY_REFERENCE,
                        queryEmbedding,
                        properties.topK().categoryReference()
                ),
                repository.searchByItemType(
                        RetrievalItemType.OFFICIAL_GUIDE,
                        queryEmbedding,
                        properties.topK().officialGuide()
                )
        );
    }

    private void validateQueryEmbedding(EmbeddingResult queryEmbedding) {
        if (queryEmbedding == null) {
            throw new InvalidQueryEmbeddingException("query embedding은 null일 수 없습니다.");
        }
        float[] vector = queryEmbedding.vector();
        if (vector == null) {
            throw new InvalidQueryEmbeddingException("query embedding vector는 null일 수 없습니다.");
        }
        if (vector.length != 1536) {
            throw new InvalidQueryEmbeddingException("query embedding vector는 1536차원이어야 합니다.");
        }
        if (queryEmbedding.dimensions() != 1536) {
            throw new InvalidQueryEmbeddingException("query embedding dimensions는 1536이어야 합니다.");
        }
        if (queryEmbedding.version() == null || queryEmbedding.version().isBlank()) {
            throw new InvalidQueryEmbeddingException("query embedding version은 비어 있을 수 없습니다.");
        }
    }
}
