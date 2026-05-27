package com.example.ccas.retrieval.importing;

public record ImportExecutionOptions(
        boolean allowSynthetic,
        boolean dryRun
) {
    public static ImportExecutionOptions safeDefault() {
        return new ImportExecutionOptions(false, true);
    }
}
