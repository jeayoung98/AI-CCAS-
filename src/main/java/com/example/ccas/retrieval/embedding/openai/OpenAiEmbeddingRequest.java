package com.example.ccas.retrieval.embedding.openai;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAiEmbeddingRequest(
        String model,
        String input,
        int dimensions,
        @JsonProperty("encoding_format") String encodingFormat
) {
}
