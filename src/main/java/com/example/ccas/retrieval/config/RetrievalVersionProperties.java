package com.example.ccas.retrieval.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "retrieval")
public record RetrievalVersionProperties(
        @Valid @NotNull StructureProperties structure,
        @Valid @NotNull SearchTextVersions searchText
) {

    public String structureVersion() {
        return structure.version();
    }

    public String complaintSearchTextVersion() {
        return searchText.complaintVersion();
    }

    public String categoryReferenceSearchTextVersion() {
        return searchText.categoryReferenceVersion();
    }

    public String officialGuideSearchTextVersion() {
        return searchText.officialGuideVersion();
    }

    public record StructureProperties(
            @NotBlank String version
    ) {
    }

    public record SearchTextVersions(
            @NotBlank String complaintVersion,
            @NotBlank String categoryReferenceVersion,
            @NotBlank String officialGuideVersion
    ) {
    }
}
