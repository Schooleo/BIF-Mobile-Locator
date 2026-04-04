package com.bif.server.features.ai.services;

import com.bif.server.features.ai.agents.PlaceSuggestionAgent;
import com.bif.server.features.ai.agents.TripDraftingAgent;
import com.bif.server.features.ai.config.OllamaProperties;
import com.bif.server.features.ai.dto.GeneratedItinerary;
import com.bif.server.features.ai.dto.GeneratedStop;
import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.ai.dto.graphql.AiFailureCode;
import com.bif.server.features.ai.dto.graphql.AiPlaceSuggestionResult;
import com.bif.server.features.ai.dto.graphql.AiTripDraftResult;
import com.bif.server.features.ai.exceptions.AiParseException;
import com.bif.server.features.place.models.Place;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiOrchestratorServiceTest {

    @Mock
    private PlaceSuggestionAgent placeSuggestionAgent;

    @Mock
    private TripDraftingAgent tripDraftingAgent;

    @Mock
    private AiPlaceGroundingService aiPlaceGroundingService;

    @Mock
    private AiRequestGuardService aiRequestGuardService;

    private AiOrchestratorService aiOrchestratorService;

    @BeforeEach
    void setUp() {
        OllamaProperties properties = new OllamaProperties();
        properties.setRetryCount(1);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-1", null, "ROLE_USER")
        );
        aiOrchestratorService = new AiOrchestratorService(
                placeSuggestionAgent,
                tripDraftingAgent,
                aiPlaceGroundingService,
                aiRequestGuardService,
                properties
        );
        when(aiRequestGuardService.evaluateCurrentRequest())
                .thenReturn(AiRequestDecision.allowed("user-1"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void suggestPlacesFromQuery_ReturnsGroundedPlaces() {
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("coffee"),
                "cafe",
                "quiet"
        );
        Place place = place("p1", "Cafe");
        when(placeSuggestionAgent.extract("coffee")).thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction)).thenReturn(List.of(place));

        AiPlaceSuggestionResult result =
                aiOrchestratorService.suggestPlacesFromQuery("coffee");

        assertNull(result.failureCode());
        assertEquals("cafe", result.category());
        assertEquals(1, result.places().size());
    }

    @Test
    void suggestPlacesFromQuery_RetriesAfterParseFailure() {
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("museum"),
                "history",
                null
        );
        when(placeSuggestionAgent.extract("museum"))
                .thenThrow(new AiParseException("bad json"));
        when(placeSuggestionAgent.retry("museum", "bad json"))
                .thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction))
                .thenReturn(List.of());

        AiPlaceSuggestionResult result =
                aiOrchestratorService.suggestPlacesFromQuery("museum");

        assertEquals(AiFailureCode.NO_RESULTS, result.failureCode());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("Retried place suggestion")));
        verify(placeSuggestionAgent).retry("museum", "bad json");
    }

    @Test
    void suggestPlacesFromQuery_DeniesUnauthorizedCallerBeforeAgentWork() {
        when(aiRequestGuardService.evaluateCurrentRequest()).thenReturn(
                AiRequestDecision.denied(
                        AiFailureCode.UNAUTHORIZED,
                        "Authentication is required for AI mutations."
                )
        );

        AiPlaceSuggestionResult result =
                aiOrchestratorService.suggestPlacesFromQuery("coffee");

        assertEquals(AiFailureCode.UNAUTHORIZED, result.failureCode());
        assertTrue(result.places().isEmpty());
        verify(placeSuggestionAgent, never()).extract("coffee");
    }

    @Test
    void draftTripFromQuery_RetriesWhenDraftUsesUnknownPlaceId() {
        String query = "build a day trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("coffee"),
                "cafe",
                null
        );
        Place place = place("p1", "Cafe");
        List<Place> candidates = List.of(place);
        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Bad Draft",
                null,
                List.of(new GeneratedStop("missing", 60, null))
        );
        GeneratedItinerary validDraft = new GeneratedItinerary(
                "Good Draft",
                "Nice route",
                List.of(new GeneratedStop("p1", 60, "Start here"))
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(query, candidates)).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("unknown placeId")
        )).thenReturn(validDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(1, result.candidatePlaces().size());
        assertEquals("p1", result.draft().stops().getFirst().placeId());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("Retried trip drafting")));
    }

    @Test
    void draftTripFromQuery_ReturnsFailureAfterRepeatedParseError() {
        String query = "make a trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(query, candidates))
                .thenThrow(new AiParseException("bad draft"));
        when(tripDraftingAgent.retry(query, candidates, "bad draft"))
                .thenThrow(new AiParseException("still bad"));

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.AI_PARSE_FAILURE, result.failureCode());
        assertTrue(result.warnings().contains("still bad"));
    }

    @Test
    void draftTripFromQuery_RetriesWhenDraftContainsDuplicatePlaceIds() {
        String query = "build a day trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("coffee"),
                "cafe",
                null
        );
        Place first = place("p1", "Cafe 1");
        Place second = place("p2", "Cafe 2");
        List<Place> candidates = List.of(first, second);
        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Bad Draft",
                null,
                List.of(
                        new GeneratedStop("p1", 60, null),
                        new GeneratedStop("p1", 60, null)
                )
        );
        GeneratedItinerary validDraft = new GeneratedItinerary(
                "Good Draft",
                "Better route",
                List.of(
                        new GeneratedStop("p1", 60, "Start"),
                        new GeneratedStop("p2", 60, "Continue")
                )
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(query, candidates)).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("duplicate placeId")
        )).thenReturn(validDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(2, result.draft().stops().size());
        assertEquals("p1", result.draft().stops().get(0).placeId());
        assertEquals("p2", result.draft().stops().get(1).placeId());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("Retried trip drafting")));
    }

    @Test
    void draftTripFromQuery_ReturnsValidationFailureAfterRepeatedDurationViolation() {
        String query = "make a trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Bad Draft",
                null,
                List.of(new GeneratedStop("p1", 5, null))
        );
        GeneratedItinerary invalidRetry = new GeneratedItinerary(
                "Still Bad",
                null,
                List.of(new GeneratedStop("p1", 500, null))
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(query, candidates)).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("out-of-range durationMinutes")
        )).thenReturn(invalidRetry);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.AI_VALIDATION_FAILURE, result.failureCode());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("out-of-range durationMinutes")));
    }

    @Test
    void draftTripFromQuery_ReturnsSafeFailureWhenNoCandidatesExist() {
        String query = "make a trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("park"),
                null,
                null
        );
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction)).thenReturn(List.of());

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.NO_CANDIDATE_PLACES, result.failureCode());
        assertTrue(result.candidatePlaces().isEmpty());
    }

    @Test
    void draftTripFromQuery_FallsBackToDefaultTitleWhenBlank() {
        String query = "plan a short trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        GeneratedItinerary itinerary = new GeneratedItinerary(
                "   ",
                "Summary",
                List.of(new GeneratedStop("p1", 60, null))
        );
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiPlaceGroundingService.ground(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(query, candidates)).thenReturn(itinerary);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals("AI Trip Draft", result.draft().title());
    }

    @Test
    void draftTripFromQuery_DeniesRateLimitedCallerBeforeAgentWork() {
        when(aiRequestGuardService.evaluateCurrentRequest()).thenReturn(
                AiRequestDecision.denied(
                        AiFailureCode.RATE_LIMITED,
                        "AI request rate limit exceeded. Please try again later."
                )
        );

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery("day trip");

        assertEquals(AiFailureCode.RATE_LIMITED, result.failureCode());
        assertTrue(result.candidatePlaces().isEmpty());
        verify(placeSuggestionAgent, never()).extract("day trip");
        verify(tripDraftingAgent, never()).draft(eq("day trip"), anyList());
    }

    private Place place(String id, String name) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        return place;
    }
}
