package com.example.ccas.retrieval.domain.type;

public enum RelationshipType {
    NEIGHBOR("이웃"),
    FAMILY("가족"),
    STRANGER("모르는 사람"),
    TRANSACTION_PARTY("거래 상대방"),
    ORGANIZATION("기관 또는 단체"),
    OTHER("기타 관계"),
    UNKNOWN("확인되지 않음");

    private final String searchLabel;

    RelationshipType(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
