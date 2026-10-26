package com.example.ccas.retrieval.application.search;

import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import java.util.*;
import java.util.function.ToDoubleFunction;

/** Experimental scores determine order only; cosineSimilarity always retains its original meaning. */
public final class CandidateRanker {
    private CandidateRanker() {}

    public static List<RetrievalSearchHit> rank(List<RetrievalSearchHit> vector,
            List<RetrievalSearchHit> codes, RetrievalMetadata query,
            RetrievalSearchProperties settings, int limit) {
        Map<Long, RetrievalSearchHit> union = new LinkedHashMap<>();
        vector.forEach(hit -> union.put(hit.itemId(), hit));
        codes.forEach(hit -> union.putIfAbsent(hit.itemId(), hit));
        Map<Long, Double> fusion = new HashMap<>();
        for (List<RetrievalSearchHit> channel : List.of(vector, codes)) {
            for (int i = 0; i < channel.size(); i++) {
                fusion.merge(channel.get(i).itemId(), 1.0 / (settings.rrfConstant() + i + 1), Double::sum);
            }
        }
        ToDoubleFunction<RetrievalSearchHit> score = hit -> switch (settings.strategy()) {
            case BASELINE, EXACT -> hit.cosineSimilarity();
            case METADATA -> hit.cosineSimilarity() + settings.metadataWeight() * agreement(query, hit);
            case CODE_RRF -> fusion.getOrDefault(hit.itemId(), 0.0);
            // Normalized fusion is in [0,1]; its maximum influence is explicitly bounded by weight.
            case COSINE_CODE_RRF -> hit.cosineSimilarity() + settings.metadataWeight()
                    * (settings.rrfConstant() + 1.0) * fusion.getOrDefault(hit.itemId(), 0.0) / 2.0;
        };
        List<RetrievalSearchHit> sorted = union.values().stream()
                .sorted(Comparator.comparingDouble(score).reversed().thenComparingLong(RetrievalSearchHit::itemId))
                .limit(limit).toList();
        List<RetrievalSearchHit> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            RetrievalSearchHit h = sorted.get(i);
            result.add(new RetrievalSearchHit(h.itemId(), h.itemType(), h.title(), h.structuredPayload(),
                    h.subjectMatterCodes(), h.requestedActionCodes(), h.riskSignalCodes(),
                    h.verifiedCategoryCode(), h.verifiedRouteCode(), h.cosineSimilarity(), i + 1));
        }
        return List.copyOf(result);
    }

    public static double agreement(RetrievalMetadata q, RetrievalSearchHit h) {
        if (q == null) return 0;
        // Positive-only boosts: missing or conflicting metadata never removes a candidate.
        double sum = coverage(q.subjectMatterCodes(), h.subjectMatterCodes())
                + coverage(q.requestedActionCodes(), h.requestedActionCodes())
                + coverage(q.riskSignalCodes(), h.riskSignalCodes());
        if (h.structuredPayload() != null) {
            sum += match(q.placeType(), h.structuredPayload().path("context").path("placeType").asText());
            sum += match(q.relationshipType(), h.structuredPayload().path("context").path("relationshipType").asText());
            sum += match(q.ongoingStatus(), h.structuredPayload().path("ongoingStatus").asText());
        }
        return sum / 6.0;
    }

    private static double match(String a, String b) {
        return a != null && !a.isBlank() && !a.equals("UNKNOWN") && a.equals(b) ? 1 : 0;
    }

    private static double coverage(List<String> query, List<String> document) {
        List<String> known = query.stream().filter(s -> !s.equals("UNKNOWN")).distinct().toList();
        return known.isEmpty() ? 0 : known.stream().filter(document::contains).count() / (double) known.size();
    }
}
