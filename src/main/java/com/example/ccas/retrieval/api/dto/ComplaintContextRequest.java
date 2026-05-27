package com.example.ccas.retrieval.api.dto;

import com.example.ccas.retrieval.domain.type.PlaceType;
import com.example.ccas.retrieval.domain.type.RelationshipType;
import com.example.ccas.retrieval.domain.type.TimePattern;
import jakarta.validation.constraints.NotNull;

public record ComplaintContextRequest(
        @NotNull PlaceType placeType,
        @NotNull RelationshipType relationshipType,
        @NotNull TimePattern timePattern
) {
}
