package com.example.ccas.retrieval.embedding.openai;

import java.util.List;

public record OpenAiEmbeddingResponse(
        List<OpenAiEmbeddingData> data
) {
}
