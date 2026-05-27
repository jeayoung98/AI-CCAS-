package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import com.example.ccas.retrieval.domain.type.InputSource;
import com.example.ccas.retrieval.embedding.EmbeddingResult;

import java.util.UUID;

public record RetrievalQueryInsertRow(
        UUID id,
        InputSource inputSource,
        boolean maskingCompleted,
        String maskedText,
        String structuredPayloadJson,
        String searchText,
        RetrievalMetadata metadata,
        EmbeddingResult embeddingResult,
        String structureVersion,
        String searchTextVersion
) {
}
