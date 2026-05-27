package com.example.ccas.retrieval.domain.type;

public enum TimePattern {
    ONE_TIME("일회성"),
    REPEATED("반복 발생"),
    ONGOING("현재 진행 중"),
    UNKNOWN("확인되지 않음");

    private final String searchLabel;

    TimePattern(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
