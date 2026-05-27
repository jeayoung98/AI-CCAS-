package com.example.ccas.retrieval.infrastructure.persistence;

import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;

public record KnowledgeSourceRecord(
        long id,
        KnowledgeSourceType sourceType,
        String title
) {
}
