package com.example.ccas.retrieval.domain.type;

public enum FactType {
    ACTION("행위"),
    HARM("피해"),
    OBJECT("대상 또는 물체"),
    LOCATION_CONTEXT("장소 맥락"),
    TIME_CONTEXT("시간 맥락");

    private final String searchLabel;

    FactType(String searchLabel) {
        this.searchLabel = searchLabel;
    }

    public String searchLabel() {
        return searchLabel;
    }
}
