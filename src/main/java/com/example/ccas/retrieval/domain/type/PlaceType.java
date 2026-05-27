package com.example.ccas.retrieval.domain.type;

public enum PlaceType {
    RESIDENCE("주거지"),
    ROAD("도로"),
    ONLINE("온라인"),
    PUBLIC_FACILITY("공공시설"),
    POLICE_SERVICE("경찰 서비스 이용 과정"),
    OTHER("기타 장소"),
    UNKNOWN("확인되지 않음");

    private final String searchLabel;

    PlaceType(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
