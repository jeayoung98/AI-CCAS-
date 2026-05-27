package com.example.ccas.retrieval.application.ingestion;

import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;

public class InvalidKnowledgeSourceTypeException extends RuntimeException {

    public InvalidKnowledgeSourceTypeException(
            RetrievalItemType itemType,
            KnowledgeSourceType expected,
            KnowledgeSourceType actual
    ) {
        super("item_type " + itemType.name() + "에는 source_type " + expected.name()
                + "이 필요하지만 실제 값은 " + actual.name() + "입니다.");
    }
}
