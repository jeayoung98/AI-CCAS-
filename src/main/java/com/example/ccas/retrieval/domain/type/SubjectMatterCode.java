package com.example.ccas.retrieval.domain.type;

public enum SubjectMatterCode {
    SAFETY_THREAT("안전 위협"),
    LOST_ITEM("유실물"),
    TRAFFIC_ADMIN("교통 행정"),
    CYBER_TRANSACTION("온라인 거래 피해 의심"),
    PATROL_REQUEST("순찰 요청"),
    CASE_STATUS("사건 또는 민원 처리 현황"),
    POLICE_SERVICE_COMPLAINT("경찰 서비스 관련 민원"),
    GENERAL_CONSULTATION("일반 경찰 민원 상담"),
    OTHER("기타 민원"),
    UNKNOWN("확인되지 않음");

    private final String searchLabel;

    SubjectMatterCode(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
