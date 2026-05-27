package com.example.ccas.retrieval.api.dto;

public record ScoreSummaryResponse(
        Double bestCaseScore,
        Double bestCategoryScore,
        Double bestGuideScore,
        Double categoryMargin,
        Double caseCategoryAgreement
) {
}
