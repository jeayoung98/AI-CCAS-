package com.example.ccas.retrieval.domain;

import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.TimePattern;
import jakarta.validation.constraints.NotNull;

public record ComplaintContext(
        @NotNull PlaceType placeType,
        @NotNull RelationshipType relationshipType,
        @NotNull TimePattern timePattern
) {
}
