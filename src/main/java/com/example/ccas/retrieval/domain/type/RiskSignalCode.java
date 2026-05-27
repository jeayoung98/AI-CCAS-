package com.example.ccas.retrieval.domain.type;

public enum RiskSignalCode {
    WEAPON("흉기 또는 무기 관련 위험"),
    DEATH_THREAT("살해 협박성 표현"),
    ASSAULT_IN_PROGRESS("현재 폭행 진행 가능성"),
    ABDUCTION("납치 또는 감금 가능성"),
    MISSING_PERSON("실종 관련 상황"),
    CHILD_ABUSE("아동학대 의심"),
    STALKING_PATTERN("반복 접근 또는 감시 정황"),
    PERSONAL_DATA_EXPOSURE("개인정보 노출 또는 유포");

    private final String searchLabel;

    RiskSignalCode(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
