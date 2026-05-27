package com.example.ccas.retrieval.domain.type;

public enum OngoingStatus {
    CURRENTLY_HAPPENING("현재 진행 중"),
    PAST_EVENT("과거 발생"),
    REPEATED_AND_MAY_CONTINUE("반복되며 다시 발생할 가능성이 있음"),
    UNKNOWN("확인되지 않음");

    private final String searchLabel;

    OngoingStatus(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
