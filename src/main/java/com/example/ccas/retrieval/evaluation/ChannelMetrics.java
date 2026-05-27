package com.example.ccas.retrieval.evaluation;

public record ChannelMetrics(
        Double recallAtK,
        Double precisionAtK,
        Double mrr,
        int evaluatedQueryCount,
        int topK
) {
}
