package com.example.ccas.retrieval.experiment.report;

import com.example.ccas.retrieval.experiment.dto.ExpectedSimilarityQuery;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class SimilarityMetricsCalculator {

    public QuerySimilarityResult calculateQuery(ExpectedSimilarityQuery expected, List<SimilaritySearchHit> top5) {
        Set<Long> relevantIds = new HashSet<>(expected.expectedRelevantIds());
        boolean hitAt1 = !top5.isEmpty() && relevantIds.contains(top5.getFirst().id());
        boolean hitAt3 = top5.stream().limit(3).anyMatch(hit -> relevantIds.contains(hit.id()));
        long hitCount = top5.stream().filter(hit -> relevantIds.contains(hit.id())).count();
        double recallAt5 = hitCount / (double) relevantIds.size();
        double reciprocalRank = reciprocalRank(top5, relevantIds);
        return new QuerySimilarityResult(
                expected.queryId(),
                expected.label(),
                List.copyOf(expected.expectedRelevantIds()),
                List.copyOf(top5),
                hitAt1,
                hitAt3,
                recallAt5,
                reciprocalRank
        );
    }

    public SimilarityMetrics calculateMetrics(List<QuerySimilarityResult> results) {
        int count = results.size();
        return new SimilarityMetrics(
                results.stream().filter(QuerySimilarityResult::hitAt1).count() / (double) count,
                results.stream().filter(QuerySimilarityResult::hitAt3).count() / (double) count,
                results.stream().mapToDouble(QuerySimilarityResult::recallAt5).sum() / count,
                results.stream().mapToDouble(QuerySimilarityResult::reciprocalRank).sum() / count
        );
    }

    private double reciprocalRank(List<SimilaritySearchHit> top5, Set<Long> relevantIds) {
        for (int index = 0; index < top5.size(); index++) {
            if (relevantIds.contains(top5.get(index).id())) {
                return 1.0 / (index + 1);
            }
        }
        return 0.0;
    }
}
