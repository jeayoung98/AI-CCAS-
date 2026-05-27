package com.example.ccas.retrieval.importing;

import com.example.ccas.retrieval.application.ingestion.CreateKnowledgeSourceCommand;
import com.example.ccas.retrieval.application.ingestion.IngestCategoryReferenceCommand;
import com.example.ccas.retrieval.application.ingestion.IngestOfficialGuideCommand;
import com.example.ccas.retrieval.application.ingestion.IngestVerifiedCaseCommand;
import com.example.ccas.retrieval.application.ingestion.KnowledgeSourceIngestionService;
import com.example.ccas.retrieval.application.ingestion.RetrievalItemIngestionService;
import com.example.ccas.retrieval.config.EmbeddingProperties;
import com.example.ccas.retrieval.config.RetrievalVersionProperties;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.example.ccas.retrieval.domain.type.ReviewStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RetrievalKnowledgeImportService {

    private final RetrievalKnowledgeImportLoader loader;
    private final KnowledgeSourceIngestionService sourceIngestionService;
    private final RetrievalItemIngestionService itemIngestionService;
    private final RetrievalVersionProperties versionProperties;
    private final EmbeddingProperties embeddingProperties;
    private final Validator validator;

    public RetrievalKnowledgeImportService(
            RetrievalKnowledgeImportLoader loader,
            KnowledgeSourceIngestionService sourceIngestionService,
            RetrievalItemIngestionService itemIngestionService,
            RetrievalVersionProperties versionProperties,
            EmbeddingProperties embeddingProperties,
            Validator validator
    ) {
        this.loader = loader;
        this.sourceIngestionService = sourceIngestionService;
        this.itemIngestionService = itemIngestionService;
        this.versionProperties = versionProperties;
        this.embeddingProperties = embeddingProperties;
        this.validator = validator;
    }

    public RetrievalKnowledgeImportReport importDataset(Path datasetPath, ImportExecutionOptions options) {
        return importDataset(loader.load(datasetPath), options);
    }

    public RetrievalKnowledgeImportReport importDataset(
            RetrievalKnowledgeImportDataset dataset,
            ImportExecutionOptions options
    ) {
        ImportExecutionOptions effectiveOptions = options == null ? ImportExecutionOptions.safeDefault() : options;
        validate(dataset);
        validateExecutionPolicy(dataset, effectiveOptions);
        RetrievalKnowledgeImportReport report = buildReport(dataset, effectiveOptions.dryRun(), List.of());
        if (effectiveOptions.dryRun()) {
            return report;
        }

        List<String> importedExternalKeys = new ArrayList<>();
        for (ImportKnowledgeSource source : dataset.sources()) {
            long sourceId = sourceIngestionService.createSource(new CreateKnowledgeSourceCommand(
                    source.sourceType(),
                    source.title(),
                    source.sourceOrganization(),
                    source.sourceUrl(),
                    source.publishedAt(),
                    source.checkedAt()
            ));
            for (ImportRetrievalItem item : source.items()) {
                importedExternalKeys.add(importItem(sourceId, item));
            }
        }

        return buildReport(dataset, false, importedExternalKeys);
    }

    private void validate(RetrievalKnowledgeImportDataset dataset) {
        Set<ConstraintViolation<RetrievalKnowledgeImportDataset>> violations = validator.validate(dataset);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new RetrievalKnowledgeImportException(message);
        }
    }

    private void validateExecutionPolicy(RetrievalKnowledgeImportDataset dataset, ImportExecutionOptions options) {
        if (dataset.synthetic() && !options.allowSynthetic()) {
            throw new RetrievalKnowledgeImportException("Synthetic import dataset is not allowed by execution options.");
        }
        if (!dataset.synthetic() && (isBlank(dataset.reviewedBy()) || dataset.reviewedAt() == null)) {
            throw new RetrievalKnowledgeImportException("Non-synthetic import dataset requires reviewedBy and reviewedAt.");
        }
        if (!dataset.structureVersion().equals(versionProperties.structureVersion())) {
            throw new RetrievalKnowledgeImportException("Import dataset structureVersion does not match application configuration.");
        }
        if (!dataset.embeddingVersion().equals(embeddingProperties.version())) {
            throw new RetrievalKnowledgeImportException("Import dataset embeddingVersion does not match application configuration.");
        }
    }

    private String importItem(long sourceId, ImportRetrievalItem item) {
        switch (item.itemType()) {
            case VERIFIED_CASE -> itemIngestionService.ingestVerifiedCase(new IngestVerifiedCaseCommand(
                    sourceId,
                    item.externalKey(),
                    item.title(),
                    item.maskedText(),
                    item.verifiedCase(),
                    item.verifiedCategoryCode(),
                    item.verifiedRouteCode(),
                    item.reviewStatus()
            ));
            case CATEGORY_REFERENCE -> itemIngestionService.ingestCategoryReference(new IngestCategoryReferenceCommand(
                    sourceId,
                    item.externalKey(),
                    item.title(),
                    item.categoryReference(),
                    item.reviewStatus()
            ));
            case OFFICIAL_GUIDE -> itemIngestionService.ingestOfficialGuide(new IngestOfficialGuideCommand(
                    sourceId,
                    item.externalKey(),
                    item.title(),
                    item.officialGuideChunk(),
                    item.reviewStatus()
            ));
        }
        return item.externalKey();
    }

    private RetrievalKnowledgeImportReport buildReport(
            RetrievalKnowledgeImportDataset dataset,
            boolean dryRun,
            List<String> importedExternalKeys
    ) {
        List<ImportRetrievalItem> items = dataset.sources().stream()
                .flatMap(source -> source.items().stream())
                .toList();
        List<String> nonSearchableExternalKeys = items.stream()
                .filter(item -> item.reviewStatus() != ReviewStatus.VERIFIED)
                .map(ImportRetrievalItem::externalKey)
                .toList();
        return new RetrievalKnowledgeImportReport(
                dataset.datasetId(),
                dataset.synthetic(),
                dryRun,
                dataset.sources().size(),
                items.size(),
                countByType(items, RetrievalItemType.VERIFIED_CASE),
                countByType(items, RetrievalItemType.CATEGORY_REFERENCE),
                countByType(items, RetrievalItemType.OFFICIAL_GUIDE),
                (int) items.stream().filter(item -> item.reviewStatus() == ReviewStatus.VERIFIED).count(),
                importedExternalKeys,
                nonSearchableExternalKeys
        );
    }

    private int countByType(List<ImportRetrievalItem> items, RetrievalItemType itemType) {
        return (int) items.stream().filter(item -> item.itemType() == itemType).count();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
