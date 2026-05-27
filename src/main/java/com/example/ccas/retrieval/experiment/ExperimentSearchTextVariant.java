package com.example.ccas.retrieval.experiment;

import java.util.Locale;

public enum ExperimentSearchTextVariant {
    WITH_TITLE,
    WITHOUT_TITLE;

    public String reportName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
