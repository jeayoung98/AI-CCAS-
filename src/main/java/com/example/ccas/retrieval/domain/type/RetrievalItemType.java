package com.example.ccas.retrieval.domain.type;

public enum RetrievalItemType {
    VERIFIED_CASE("비식별 처리되고 검수 완료된 유사 민원 사례"),
    CATEGORY_REFERENCE("민원 대상 또는 유형 판단 기준"),
    OFFICIAL_GUIDE("공식 안내 문서의 검색 가능한 chunk");

    private final String description;

    RetrievalItemType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
