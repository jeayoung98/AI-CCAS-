package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.RequestedActionCode;
import com.example.ccas.retrieval.domain.type.SubjectMatterCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalItemValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsInvalidCategoryReferenceValues() {
        assertCategoryReferenceInvalid(baseCategoryReference(" ", "유실물", "설명", List.of("포함"),
                List.of(SubjectMatterCode.LOST_ITEM), List.of(RequestedActionCode.SEARCH)), "categoryCode");
        assertCategoryReferenceInvalid(baseCategoryReference("CODE", " ", "설명", List.of("포함"),
                List.of(SubjectMatterCode.LOST_ITEM), List.of(RequestedActionCode.SEARCH)), "categoryName");
        assertCategoryReferenceInvalid(baseCategoryReference("CODE", "유실물", " ", List.of("포함"),
                List.of(SubjectMatterCode.LOST_ITEM), List.of(RequestedActionCode.SEARCH)), "description");
        assertCategoryReferenceInvalid(baseCategoryReference("CODE", "유실물", "설명", List.of(),
                List.of(SubjectMatterCode.LOST_ITEM), List.of(RequestedActionCode.SEARCH)), "inclusionCriteria");
        assertCategoryReferenceInvalid(baseCategoryReference("CODE", "유실물", "설명", List.of("포함"),
                List.of(), List.of(RequestedActionCode.SEARCH)), "subjectMatters");
        assertCategoryReferenceInvalid(baseCategoryReference("CODE", "유실물", "설명", List.of("포함"),
                List.of(SubjectMatterCode.LOST_ITEM), List.of()), "supportedActions");
    }

    @Test
    void rejectsInvalidOfficialGuideChunkValues() {
        assertOfficialGuideInvalid(baseOfficialGuide(" ", "유실물 민원 안내", "안내", List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH), List.of()), "documentTitle");
        assertOfficialGuideInvalid(baseOfficialGuide("경찰 민원 안내", " ", "안내", List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH), List.of()), "sectionTitle");
        assertOfficialGuideInvalid(baseOfficialGuide("경찰 민원 안내", "유실물 민원 안내", " ", List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH), List.of()), "chunkText");
        assertOfficialGuideInvalid(baseOfficialGuide("경찰 민원 안내", "유실물 민원 안내", "안내", List.of(),
                List.of(RequestedActionCode.SEARCH), List.of()), "subjectMatters");
        assertOfficialGuideInvalid(baseOfficialGuide("경찰 민원 안내", "유실물 민원 안내", "안내", List.of(SubjectMatterCode.LOST_ITEM),
                null, List.of()), "relatedActions");
        assertOfficialGuideInvalid(baseOfficialGuide("경찰 민원 안내", "유실물 민원 안내", "안내", List.of(SubjectMatterCode.LOST_ITEM),
                List.of(RequestedActionCode.SEARCH), null), "relatedRiskSignals");
    }

    private void assertCategoryReferenceInvalid(CategoryReference reference, String propertyPath) {
        Set<ConstraintViolation<CategoryReference>> violations = validator.validate(reference);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyPath);
    }

    private void assertOfficialGuideInvalid(OfficialGuideChunk guideChunk, String propertyPath) {
        Set<ConstraintViolation<OfficialGuideChunk>> violations = validator.validate(guideChunk);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(propertyPath);
    }

    private CategoryReference baseCategoryReference(
            String categoryCode,
            String categoryName,
            String description,
            List<String> inclusionCriteria,
            List<SubjectMatterCode> subjectMatters,
            List<RequestedActionCode> supportedActions
    ) {
        return new CategoryReference(
                categoryCode,
                categoryName,
                description,
                inclusionCriteria,
                List.of(),
                List.of(),
                subjectMatters,
                supportedActions,
                true
        );
    }

    private OfficialGuideChunk baseOfficialGuide(
            String documentTitle,
            String sectionTitle,
            String chunkText,
            List<SubjectMatterCode> subjectMatters,
            List<RequestedActionCode> relatedActions,
            List<com.example.ccas.retrieval.domain.type.RiskSignalCode> relatedRiskSignals
    ) {
        return new OfficialGuideChunk(
                documentTitle,
                sectionTitle,
                chunkText,
                subjectMatters,
                relatedActions,
                relatedRiskSignals
        );
    }
}
