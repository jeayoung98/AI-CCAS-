package com.example.ccas.retrieval.application.evaluation;

import com.example.ccas.retrieval.domain.type.HitRejectionReason;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;

public record RetrievalHitAssessment(
        RetrievalItemType channel,
        long itemId,
        int rank,
        boolean passedChannelCutoff,
        HitRejectionReason rejectionReason
) {
    public RetrievalHitAssessment {
        if (passedChannelCutoff && rejectionReason != null) {
            throw new IllegalArgumentException("Passed hit must not have a rejection reason.");
        }
        if (!passedChannelCutoff && rejectionReason == null) {
            throw new IllegalArgumentException("Rejected hit must have a rejection reason.");
        }
    }
}
