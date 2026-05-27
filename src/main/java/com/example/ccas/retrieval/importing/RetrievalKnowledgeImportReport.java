package com.example.ccas.retrieval.importing;

import java.util.List;

public record RetrievalKnowledgeImportReport(
        String datasetId,
        boolean synthetic,
        boolean dryRun,
        int sourceCount,
        int totalItemCount,
        int verifiedCaseCount,
        int categoryReferenceCount,
        int officialGuideCount,
        int searchableVerifiedItemCount,
        List<String> importedExternalKeys,
        List<String> nonSearchableExternalKeys
) {
    public RetrievalKnowledgeImportReport {
        importedExternalKeys = List.copyOf(importedExternalKeys);
        nonSearchableExternalKeys = List.copyOf(nonSearchableExternalKeys);
    }
}
