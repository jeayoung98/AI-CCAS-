package com.example.ccas.retrieval.application.search;

import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.embedding.EmbeddingResult;
import com.example.ccas.retrieval.infrastructure.persistence.PgVectorRetrievalSearchRepository;
import org.springframework.stereotype.Service;
import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import java.util.List;

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
        return retrieveCandidates(queryEmbedding, null);
    }

    public RetrievalCandidates retrieveCandidates(EmbeddingResult queryEmbedding, RetrievalMetadata metadata) {
        validateQueryEmbedding(queryEmbedding);

        return new RetrievalCandidates(
                search(
                        RetrievalItemType.VERIFIED_CASE,
                        queryEmbedding,
                        properties.topK().verifiedCase(), metadata
                ),
                search(
                        RetrievalItemType.CATEGORY_REFERENCE,
                        queryEmbedding,
                        properties.topK().categoryReference(), metadata
                ),
                search(
                        RetrievalItemType.OFFICIAL_GUIDE,
                        queryEmbedding,
                        properties.topK().officialGuide(), metadata
                )
        );
    }

    private List<RetrievalSearchHit> search(RetrievalItemType type, EmbeddingResult embedding,
            int topK, RetrievalMetadata metadata) {
        if (properties.strategy() == RetrievalSearchProperties.Strategy.BASELINE) {
            return repository.searchByItemType(type, embedding, topK);
        }
        if (properties.strategy() == RetrievalSearchProperties.Strategy.EXACT) {
            return repository.searchExactCandidates(type, embedding, topK);
        }
        if (metadata == null) throw new IllegalArgumentException("Experimental strategies require query metadata");
        int limit = Math.max(topK, properties.candidateLimit());
        var vector = repository.searchExactCandidates(type, embedding, limit);
        var codes = (properties.strategy() == RetrievalSearchProperties.Strategy.CODE_RRF
                || properties.strategy() == RetrievalSearchProperties.Strategy.COSINE_CODE_RRF)
                ? repository.searchCodeCandidates(type, embedding, limit, metadata) : List.<RetrievalSearchHit>of();
        return CandidateRanker.rank(vector, codes, metadata, properties, topK);
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
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new InvalidQueryEmbeddingException("query vector must be finite");
            norm += (double) value * value;
        }
        if (norm == 0) throw new InvalidQueryEmbeddingException("query vector must be nonzero");
    }
}
