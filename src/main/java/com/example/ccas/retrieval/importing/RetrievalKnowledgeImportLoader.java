package com.example.ccas.retrieval.importing;

import com.example.ccas.retrieval.domain.type.KnowledgeSourceType;
import com.example.ccas.retrieval.domain.type.RetrievalItemType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RetrievalKnowledgeImportLoader {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public RetrievalKnowledgeImportLoader(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public RetrievalKnowledgeImportDataset load(Path path) {
        if (path == null || !Files.exists(path)) {
            throw new RetrievalKnowledgeImportException("Import dataset file does not exist.");
        }
        try {
            RetrievalKnowledgeImportDataset dataset = objectMapper.readValue(path.toFile(), RetrievalKnowledgeImportDataset.class);
            validate(dataset);
            validateRules(dataset);
            return dataset;
        } catch (IOException exception) {
            throw new RetrievalKnowledgeImportException("Failed to parse retrieval knowledge import dataset.", exception);
        }
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

    private void validateRules(RetrievalKnowledgeImportDataset dataset) {
        if (!dataset.synthetic() && (isBlank(dataset.reviewedBy()) || dataset.reviewedAt() == null)) {
            throw new RetrievalKnowledgeImportException("Non-synthetic import dataset requires reviewedBy and reviewedAt.");
        }
        for (ImportKnowledgeSource source : dataset.sources()) {
            validateSourceReviewMetadata(dataset.synthetic(), source);
            for (ImportRetrievalItem item : source.items()) {
                validateSourceItemCombination(source, item);
                validateItemPayload(item);
            }
        }
    }

    private void validateSourceReviewMetadata(boolean synthetic, ImportKnowledgeSource source) {
        if (synthetic) {
            return;
        }
        if (source.sourceType() == KnowledgeSourceType.OFFICIAL_DOCUMENT
                && (isBlank(source.sourceOrganization()) || isBlank(source.sourceUrl()) || source.checkedAt() == null)) {
            throw new RetrievalKnowledgeImportException("OFFICIAL_DOCUMENT requires sourceOrganization, sourceUrl, and checkedAt.");
        }
        if ((source.sourceType() == KnowledgeSourceType.CATEGORY_POLICY
                || source.sourceType() == KnowledgeSourceType.TEAM_VERIFIED_CASESET)
                && source.checkedAt() == null) {
            throw new RetrievalKnowledgeImportException(source.sourceType() + " requires checkedAt.");
        }
    }

    private void validateSourceItemCombination(ImportKnowledgeSource source, ImportRetrievalItem item) {
        KnowledgeSourceType expectedSourceType = switch (item.itemType()) {
            case VERIFIED_CASE -> KnowledgeSourceType.TEAM_VERIFIED_CASESET;
            case CATEGORY_REFERENCE -> KnowledgeSourceType.CATEGORY_POLICY;
            case OFFICIAL_GUIDE -> KnowledgeSourceType.OFFICIAL_DOCUMENT;
        };
        if (source.sourceType() != expectedSourceType) {
            throw new RetrievalKnowledgeImportException("Source type " + source.sourceType()
                    + " cannot contain item type " + item.itemType() + ".");
        }
    }

    private void validateItemPayload(ImportRetrievalItem item) {
        int payloadCount = (item.verifiedCase() == null ? 0 : 1)
                + (item.categoryReference() == null ? 0 : 1)
                + (item.officialGuideChunk() == null ? 0 : 1);
        if (payloadCount != 1) {
            throw new RetrievalKnowledgeImportException("Import item must contain exactly one typed payload: " + item.externalKey());
        }

        switch (item.itemType()) {
            case VERIFIED_CASE -> validateVerifiedCaseItem(item);
            case CATEGORY_REFERENCE -> validateCategoryReferenceItem(item);
            case OFFICIAL_GUIDE -> validateOfficialGuideItem(item);
        }
    }

    private void validateVerifiedCaseItem(ImportRetrievalItem item) {
        if (item.verifiedCase() == null || isBlank(item.maskedText()) || isBlank(item.verifiedCategoryCode())) {
            throw new RetrievalKnowledgeImportException("VERIFIED_CASE requires verifiedCase, maskedText, and verifiedCategoryCode.");
        }
    }

    private void validateCategoryReferenceItem(ImportRetrievalItem item) {
        if (item.categoryReference() == null || item.verifiedCase() != null || item.officialGuideChunk() != null) {
            throw new RetrievalKnowledgeImportException("CATEGORY_REFERENCE requires categoryReference only.");
        }
        if (!isBlank(item.maskedText()) || !isBlank(item.verifiedRouteCode())) {
            throw new RetrievalKnowledgeImportException("CATEGORY_REFERENCE must not contain maskedText or verifiedRouteCode.");
        }
        if (!isBlank(item.verifiedCategoryCode())
                && !item.verifiedCategoryCode().equals(item.categoryReference().categoryCode())) {
            throw new RetrievalKnowledgeImportException("CATEGORY_REFERENCE verifiedCategoryCode must match categoryReference.categoryCode.");
        }
    }

    private void validateOfficialGuideItem(ImportRetrievalItem item) {
        if (item.officialGuideChunk() == null || item.verifiedCase() != null || item.categoryReference() != null) {
            throw new RetrievalKnowledgeImportException("OFFICIAL_GUIDE requires officialGuideChunk only.");
        }
        if (!isBlank(item.maskedText()) || !isBlank(item.verifiedCategoryCode()) || !isBlank(item.verifiedRouteCode())) {
            throw new RetrievalKnowledgeImportException("OFFICIAL_GUIDE must not contain maskedText, verifiedCategoryCode, or verifiedRouteCode.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
