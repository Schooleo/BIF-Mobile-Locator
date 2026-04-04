package com.bif.server.features.ai.services;

import com.bif.server.features.ai.AiGenerationConstraints;
import com.bif.server.features.ai.agents.PlaceSuggestionAgent;
import com.bif.server.features.ai.agents.TripDraftingAgent;
import com.bif.server.features.ai.config.OllamaProperties;
import com.bif.server.features.ai.dto.GeneratedItinerary;
import com.bif.server.features.ai.dto.GeneratedStop;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.ai.dto.graphql.AiFailureCode;
import com.bif.server.features.ai.dto.graphql.AiPlaceSuggestionResult;
import com.bif.server.features.ai.dto.graphql.AiTripDraft;
import com.bif.server.features.ai.dto.graphql.AiTripDraftResult;
import com.bif.server.features.ai.dto.graphql.AiTripDraftStop;
import com.bif.server.features.ai.exceptions.AiIntegrationException;
import com.bif.server.features.ai.exceptions.AiParseException;
import com.bif.server.features.ai.exceptions.AiUpstreamException;
import com.bif.server.features.ai.exceptions.AiValidationException;
import com.bif.server.features.place.models.Place;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiOrchestratorService {

    private final PlaceSuggestionAgent placeSuggestionAgent;
    private final TripDraftingAgent tripDraftingAgent;
    private final AiPlaceGroundingService aiPlaceGroundingService;
    private final AiRequestGuardService aiRequestGuardService;
    private final OllamaProperties ollamaProperties;

    public AiOrchestratorService(
            PlaceSuggestionAgent placeSuggestionAgent,
            TripDraftingAgent tripDraftingAgent,
            AiPlaceGroundingService aiPlaceGroundingService,
            AiRequestGuardService aiRequestGuardService,
            OllamaProperties ollamaProperties) {
        this.placeSuggestionAgent = placeSuggestionAgent;
        this.tripDraftingAgent = tripDraftingAgent;
        this.aiPlaceGroundingService = aiPlaceGroundingService;
        this.aiRequestGuardService = aiRequestGuardService;
        this.ollamaProperties = ollamaProperties;
    }

    public AiPlaceSuggestionResult suggestPlacesFromQuery(String query) {
        AiRequestDecision decision = aiRequestGuardService.evaluateCurrentRequest();
        if (!decision.allowed()) {
            return deniedSuggestion(decision);
        }
        if (query == null || query.isBlank()) {
            return new AiPlaceSuggestionResult(
                    List.of(),
                    List.of(),
                    null,
                    null,
                    List.of("Query must not be blank."),
                    AiFailureCode.INVALID_QUERY);
        }

        List<String> warnings = new ArrayList<>();
        try {
            PlaceSearchExtraction extraction = withRetry(
                    () -> placeSuggestionAgent.extract(query),
                    reason -> placeSuggestionAgent.retry(query, reason),
                    "place suggestion",
                    warnings);
            List<Place> places = aiPlaceGroundingService.ground(extraction);
            if (places.isEmpty()) {
                warnings.add("No grounded places matched the extracted parameters.");
            }
            return new AiPlaceSuggestionResult(
                    places,
                    extraction.keywords(),
                    extraction.category(),
                    extraction.vibe(),
                    warnings,
                    places.isEmpty() ? AiFailureCode.NO_RESULTS : null);
        } catch (AiIntegrationException e) {
            warnings.add(e.getMessage());
            return new AiPlaceSuggestionResult(
                    List.of(),
                    List.of(),
                    null,
                    null,
                    warnings,
                    failureCodeFor(e));
        }
    }

    public AiTripDraftResult draftTripFromQuery(String query) {
        AiRequestDecision decision = aiRequestGuardService.evaluateCurrentRequest();
        if (!decision.allowed()) {
            return deniedDraft(decision);
        }
        if (query == null || query.isBlank()) {
            return new AiTripDraftResult(
                    null,
                    List.of(),
                    List.of("Query must not be blank."),
                    AiFailureCode.INVALID_QUERY);
        }

        List<String> warnings = new ArrayList<>();
        List<Place> candidatePlaces = List.of();
        try {
            PlaceSearchExtraction extraction = withRetry(
                    () -> placeSuggestionAgent.extract(query),
                    reason -> placeSuggestionAgent.retry(query, reason),
                    "place suggestion",
                    warnings);
            candidatePlaces = aiPlaceGroundingService.ground(extraction);
            if (candidatePlaces.isEmpty()) {
                warnings.add("No candidate places were available for drafting.");
                return new AiTripDraftResult(
                        null,
                        List.of(),
                        warnings,
                        AiFailureCode.NO_CANDIDATE_PLACES);
            }
            List<Place> resolvedCandidatePlaces = candidatePlaces;

            GeneratedItinerary itinerary = withRetry(
                    () -> validateGeneratedItinerary(
                            tripDraftingAgent.draft(query, resolvedCandidatePlaces),
                            resolvedCandidatePlaces),
                    reason -> validateGeneratedItinerary(
                            tripDraftingAgent.retry(query, resolvedCandidatePlaces, reason),
                            resolvedCandidatePlaces),
                    "trip drafting",
                    warnings);

            return new AiTripDraftResult(
                    mapDraft(itinerary, resolvedCandidatePlaces),
                    resolvedCandidatePlaces,
                    warnings,
                    null);
        } catch (AiIntegrationException e) {
            warnings.add(e.getMessage());
            return new AiTripDraftResult(
                    null,
                    candidatePlaces,
                    warnings,
                    failureCodeFor(e));
        }
    }

    private AiPlaceSuggestionResult deniedSuggestion(AiRequestDecision decision) {
        return new AiPlaceSuggestionResult(
                List.of(),
                List.of(),
                null,
                null,
                List.of(decision.message()),
                decision.failureCode());
    }

    private AiTripDraftResult deniedDraft(AiRequestDecision decision) {
        return new AiTripDraftResult(
                null,
                List.of(),
                List.of(decision.message()),
                decision.failureCode());
    }

    private GeneratedItinerary validateGeneratedItinerary(
            GeneratedItinerary itinerary,
            List<Place> candidatePlaces) {
        if (itinerary == null || itinerary.stops().isEmpty()) {
            throw new AiValidationException("AI draft did not contain any stops.");
        }
        if (itinerary.stops().size() > AiGenerationConstraints.MAX_STOPS) {
            throw new AiValidationException(
                    "AI draft exceeded the maximum number of stops ("
                            + AiGenerationConstraints.MAX_STOPS + ").");
        }

        Map<String, Place> placeById = indexPlaces(candidatePlaces);
        List<String> invalidPlaceIds = itinerary.stops().stream()
                .map(GeneratedStop::placeId)
                .filter(placeId -> placeId == null || !placeById.containsKey(placeId))
                .distinct()
                .toList();
        if (!invalidPlaceIds.isEmpty()) {
            throw new AiValidationException(
                    "AI draft referenced unknown placeId values: "
                            + String.join(", ", invalidPlaceIds));
        }

        long distinctPlaceCount = itinerary.stops().stream()
                .map(GeneratedStop::placeId)
                .distinct()
                .count();
        if (distinctPlaceCount < itinerary.stops().size()) {
            throw new AiValidationException(
                    "AI draft included duplicate placeId values.");
        }

        boolean hasInvalidDuration = itinerary.stops().stream()
                .anyMatch(stop -> stop.durationMinutes() == null
                        || stop.durationMinutes() < AiGenerationConstraints.MIN_STOP_DURATION_MINUTES
                        || stop.durationMinutes() > AiGenerationConstraints.MAX_STOP_DURATION_MINUTES);
        if (hasInvalidDuration) {
            throw new AiValidationException(
                    "AI draft included an out-of-range durationMinutes value.");
        }

        int totalDurationMinutes = itinerary.stops().stream()
                .mapToInt(GeneratedStop::durationMinutes)
                .sum();
        if (totalDurationMinutes > AiGenerationConstraints.MAX_TOTAL_DURATION_MINUTES) {
            throw new AiValidationException(
                    "AI draft exceeded total duration limit of "
                            + AiGenerationConstraints.MAX_TOTAL_DURATION_MINUTES
                            + " minutes.");
        }

        return itinerary;
    }

    private AiTripDraft mapDraft(
            GeneratedItinerary itinerary,
            List<Place> candidatePlaces) {
        Map<String, Place> placeById = indexPlaces(candidatePlaces);
        List<AiTripDraftStop> stops = itinerary.stops().stream()
                .map(stop -> new AiTripDraftStop(
                        stop.placeId(),
                        placeById.get(stop.placeId()),
                        stop.durationMinutes(),
                        stop.note()))
                .toList();

        return new AiTripDraft(
                safeDraftTitle(itinerary.title()),
                itinerary.summary(),
                stops);
    }

    private String safeDraftTitle(String title) {
        if (title == null || title.isBlank()) {
            return "AI Trip Draft";
        }
        return title;
    }

    private Map<String, Place> indexPlaces(List<Place> candidatePlaces) {
        Map<String, Place> placeById = new LinkedHashMap<>();
        for (Place place : candidatePlaces) {
            if (place != null && place.getId() != null) {
                placeById.put(place.getId(), place);
            }
        }
        return placeById;
    }

    private AiFailureCode failureCodeFor(AiIntegrationException exception) {
        if (exception instanceof AiParseException) {
            return AiFailureCode.AI_PARSE_FAILURE;
        }
        if (exception instanceof AiValidationException) {
            return AiFailureCode.AI_VALIDATION_FAILURE;
        }
        if (exception instanceof AiUpstreamException) {
            return AiFailureCode.AI_UPSTREAM_FAILURE;
        }
        return AiFailureCode.AI_FAILURE;
    }

    private <T> T withRetry(
            ThrowingSupplier<T> initialCall,
            RetryHandler<T> retryHandler,
            String context,
            List<String> warnings) {
        try {
            return initialCall.get();
        } catch (AiParseException | AiValidationException firstFailure) {
            AiIntegrationException lastFailure = firstFailure;
            for (int attempt = 0; attempt < ollamaProperties.getRetryCount(); attempt++) {
                warnings.add("Retried " + context + " after invalid model output.");
                try {
                    return retryHandler.retry(lastFailure.getMessage());
                } catch (AiParseException | AiValidationException retryFailure) {
                    lastFailure = retryFailure;
                }
            }
            throw lastFailure;
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }

    @FunctionalInterface
    private interface RetryHandler<T> {
        T retry(String failureReason);
    }
}
