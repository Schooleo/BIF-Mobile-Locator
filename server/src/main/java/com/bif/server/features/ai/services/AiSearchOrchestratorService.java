package com.bif.server.features.ai.services;

import com.bif.server.features.ai.AiGenerationConstraints;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.place.models.Place;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiSearchOrchestratorService {

    private final AiPlaceGroundingService aiPlaceGroundingService;

    public AiSearchOrchestratorService(AiPlaceGroundingService aiPlaceGroundingService) {
        this.aiPlaceGroundingService = aiPlaceGroundingService;
    }

    public List<Place> resolveCandidates(PlaceSearchExtraction extraction) {
        return resolveCandidates(extraction, AiGenerationConstraints.none());
    }

    public List<Place> resolveCandidates(
            PlaceSearchExtraction extraction,
            AiGenerationConstraints constraints) {
        return aiPlaceGroundingService.ground(extraction, null, null, constraints);
    }

    public List<Place> resolveCandidates(
            PlaceSearchExtraction extraction,
            Double latitude,
            Double longitude) {
        return aiPlaceGroundingService.ground(extraction, latitude, longitude);
    }

    public List<Place> resolveCandidates(
            PlaceSearchExtraction extraction,
            Double latitude,
            Double longitude,
            AiGenerationConstraints constraints) {
        return aiPlaceGroundingService.ground(extraction, latitude, longitude, constraints);
    }

    public boolean hasLocationFocus(PlaceSearchExtraction extraction) {
        return aiPlaceGroundingService.hasLocationFocus(extraction);
    }

    public boolean matchesLocationFocus(PlaceSearchExtraction extraction, Place place) {
        return aiPlaceGroundingService.matchesLocationFocus(extraction, place);
    }

    public String describeLocationFocus(PlaceSearchExtraction extraction) {
        return aiPlaceGroundingService.describeLocationFocus(extraction);
    }
}
