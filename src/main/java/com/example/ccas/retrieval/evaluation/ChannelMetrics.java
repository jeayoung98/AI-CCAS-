package com.example.ccas.retrieval.evaluation;

public record ChannelMetrics(
        Double recallAtK,
        Double precisionAtK,
        Double mrr,
        int evaluatedQueryCount,
        int topK,
        Double hitAt1,
        Double hitAt3,
        Double hitAt5,
        Double precisionAtFixedK,
        int relevantQueryCount
) {
}
