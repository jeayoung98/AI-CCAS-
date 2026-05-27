package com.example.ccas.retrieval.application.query;

import com.example.ccas.retrieval.application.evaluation.RetrievalEvidenceDecision;
import com.example.ccas.retrieval.application.search.RetrievalCandidates;

record RetrievalQueryTransactionResult(
        RetrievalCandidates candidates,
        RetrievalEvidenceDecision decision
) {
}
