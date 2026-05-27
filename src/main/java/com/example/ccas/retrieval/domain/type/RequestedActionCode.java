package com.example.ccas.retrieval.domain.type;

public enum RequestedActionCode {
    REPORT("신고 문의"),
    CONSULT("상담 요청"),
    SEARCH("검색 또는 조회 요청"),
    APPLY("신청 요청"),
    CHECK_STATUS("처리 현황 확인"),
    PROVIDE_INFORMATION("정보 제공 또는 제보"),
    UNKNOWN("확인되지 않음");

    private final String searchLabel;

    RequestedActionCode(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
