package com.example.ccas.retrieval.embedding;

public class EmbeddingRequestException extends RuntimeException {

    public EmbeddingRequestException(String message) {
        super(message);
    }

    public EmbeddingRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
