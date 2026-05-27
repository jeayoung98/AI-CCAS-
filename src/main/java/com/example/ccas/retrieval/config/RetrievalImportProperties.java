package com.example.ccas.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "retrieval.import")
public record RetrievalImportProperties(
        boolean enabled,
        String filePath,
        boolean allowSynthetic,
        boolean dryRun
) {
}
