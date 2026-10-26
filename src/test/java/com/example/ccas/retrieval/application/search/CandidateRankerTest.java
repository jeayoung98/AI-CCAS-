package com.example.ccas.retrieval.application.search;

import com.example.ccas.retrieval.application.text.RetrievalMetadata;
import com.example.ccas.retrieval.config.RetrievalSearchProperties;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CandidateRankerTest {
    private final RetrievalMetadata query = new RetrievalMetadata(List.of("LOST_ITEM"), List.of("SEARCH"),
            List.of("WEAPON"), "UNKNOWN", "UNKNOWN", "UNKNOWN");
    @Test
    void purposeBoostChangesOrderWithoutChangingCosineOrDroppingRiskEvidence() {
        var wrong = hit(1, .95, "REPORT", List.of("WEAPON"));
        var right = hit(2, .94, "SEARCH", List.of("WEAPON"));
        var settings = new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(5, 3, 3),
                RetrievalSearchProperties.Strategy.METADATA, 20, .1, 60);
        var ranked = CandidateRanker.rank(List.of(wrong, right), List.of(), query, settings, 5);
        assertThat(ranked).extracting(RetrievalSearchHit::itemId).containsExactly(2L, 1L);
        assertThat(ranked).extracting(RetrievalSearchHit::cosineSimilarity).containsExactly(.94, .95);
        assertThat(ranked).allSatisfy(h -> assertThat(h.riskSignalCodes()).containsExactly("WEAPON"));
        assertThat(ranked).extracting(RetrievalSearchHit::rank).containsExactly(1, 2);
    }
    @Test
    void unknownMetadataDoesNotSupplyPositiveEvidenceAndRrfDeduplicates() {
        var h = hit(1, .2, "UNKNOWN", List.of());
        var unknown = new RetrievalMetadata(List.of("UNKNOWN"), List.of("UNKNOWN"), List.of(), "UNKNOWN", null, null);
        assertThat(CandidateRanker.agreement(unknown, h)).isZero();
        var config = new RetrievalSearchProperties(new RetrievalSearchProperties.TopK(5, 3, 3),
                RetrievalSearchProperties.Strategy.CODE_RRF, 20, 0, 60);
        assertThat(CandidateRanker.rank(List.of(h), List.of(h), unknown, config, 5)).hasSize(1);
    }
    private RetrievalSearchHit hit(long id, double cosine, String action, List<String> risk) {
        return new RetrievalSearchHit(id, RetrievalItemType.VERIFIED_CASE, "synthetic",
                JsonNodeFactory.instance.objectNode(), List.of("LOST_ITEM"), List.of(action), risk,
                "LOST_ITEM", null, cosine, (int) id);
    }
}
