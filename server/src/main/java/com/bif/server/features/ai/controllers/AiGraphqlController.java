package com.bif.server.features.ai.controllers;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import com.bif.server.features.ai.dto.graphql.AiPlaceSuggestionResult;
import com.bif.server.features.ai.dto.graphql.AiTripDraftResult;
import com.bif.server.features.ai.services.AiOrchestratorService;

@Controller
public class AiGraphqlController {

    private final AiOrchestratorService aiOrchestratorService;

    public AiGraphqlController(AiOrchestratorService aiOrchestratorService) {
        this.aiOrchestratorService = aiOrchestratorService;
    }

    @MutationMapping
    public AiPlaceSuggestionResult suggestPlacesFromQuery(
            @Argument String query,
            @Argument Double latitude,
            @Argument Double longitude,
            @Argument String cityBias) {
        return aiOrchestratorService.suggestPlacesFromQuery(query, latitude, longitude, cityBias);
    }

    public AiPlaceSuggestionResult suggestPlacesFromQuery(String query) {
        return suggestPlacesFromQuery(query, null, null, null);
    }

    @MutationMapping
    public AiTripDraftResult draftTripFromQuery(@Argument String query) {
        return aiOrchestratorService.draftTripFromQuery(query);
    }
}
