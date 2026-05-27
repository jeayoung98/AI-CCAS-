package com.example.ccas.retrieval.application.ingestion;

public class SourceNotFoundException extends RuntimeException {

    public SourceNotFoundException(long sourceId) {
        super("knowledge_source를 찾을 수 없습니다. sourceId=" + sourceId);
    }
}
