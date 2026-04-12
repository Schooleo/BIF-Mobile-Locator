package com.bif.server.features.ai.services;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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

@ExtendWith(MockitoExtension.class)
class AiOrchestratorServiceTest {

    @Mock
    private PlaceSuggestionAgent placeSuggestionAgent;

    @Mock
    private TripDraftingAgent tripDraftingAgent;

    @Mock
    private AiSearchOrchestratorService aiSearchOrchestratorService;

    @Mock
    private AiRequestGuardService aiRequestGuardService;

    private TripScheduleHintExtractor tripScheduleHintExtractor;

    private AiOrchestratorService aiOrchestratorService;

    @BeforeEach
    void setUp() {
        OllamaProperties properties = new OllamaProperties();
        properties.setRetryCount(1);
        tripScheduleHintExtractor = new TripScheduleHintExtractor(
                Clock.fixed(
                        Instant.parse("2026-04-11T00:00:00Z"),
                        ZoneId.of("Asia/Ho_Chi_Minh")));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("user-1", null, "ROLE_USER")
        );
        aiOrchestratorService = new AiOrchestratorService(
                placeSuggestionAgent,
                tripDraftingAgent,
                aiSearchOrchestratorService,
                aiRequestGuardService,
                properties,
                tripScheduleHintExtractor
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
                List.of("coffee in district 1", "quiet cafe district 1"),
                List.of("coffee"),
                "cafe",
                "quiet"
        );
        Place place = place("p1", "Cafe");
        when(placeSuggestionAgent.extract("coffee")).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction, null, null))
                .thenReturn(List.of(place));

        AiPlaceSuggestionResult result
                = aiOrchestratorService.suggestPlacesFromQuery("coffee");

        assertNull(result.failureCode());
        assertEquals("cafe", result.category());
        assertEquals(1, result.places().size());
    }

    @Test
    void suggestPlacesFromQuery_RetriesAfterParseFailure() {
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("museum in district 1"),
                List.of("museum"),
                "history",
                null
        );
        when(placeSuggestionAgent.extract("museum"))
                .thenThrow(new AiParseException("bad json"));
        when(placeSuggestionAgent.retry("museum", "bad json"))
                .thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction, null, null))
                .thenReturn(List.of());

        AiPlaceSuggestionResult result
                = aiOrchestratorService.suggestPlacesFromQuery("museum");

        assertEquals(AiFailureCode.NO_RESULTS, result.failureCode());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("Retried place suggestion")));
        verify(placeSuggestionAgent).retry("museum", "bad json");
    }

    @Test
    void suggestPlacesFromQuery_UsesCoordinateCityWhenHintNotBackedByTerms() {
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("seafood restaurant", "Vietnamese food"),
                List.of("seafood"),
                "food",
                null,
                "Hanoi"
        );
        when(placeSuggestionAgent.extract("seafood restaurant")).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(any(), eq(10.780000d), eq(106.700000d)))
                .thenReturn(List.of());

        aiOrchestratorService.suggestPlacesFromQuery("seafood restaurant", 10.780000d, 106.700000d, null);

        ArgumentCaptor<PlaceSearchExtraction> extractionCaptor = ArgumentCaptor.forClass(PlaceSearchExtraction.class);
        verify(aiSearchOrchestratorService)
                .resolveCandidates(extractionCaptor.capture(), eq(10.780000d), eq(106.700000d));

        PlaceSearchExtraction sent = extractionCaptor.getValue();
        assertEquals("ho chi minh city", sent.locationHint());
        assertTrue(sent.searchQueries().contains("ho chi minh city"));
        assertTrue(sent.searchQueries().stream().anyMatch(
                query -> query.startsWith("near 10.780000, 106.700000")));
    }

    @Test
    void suggestPlacesFromQuery_KeepsHintWhenBackedByTerms() {
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("hanoi seafood"),
                List.of("hanoi", "seafood"),
                "food",
                null,
                "Hanoi"
        );
        when(placeSuggestionAgent.extract("hanoi seafood")).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(any(), eq(10.780000d), eq(106.700000d)))
                .thenReturn(List.of());

        aiOrchestratorService.suggestPlacesFromQuery(
                "hanoi seafood",
                10.780000d,
                106.700000d,
                null);

        ArgumentCaptor<PlaceSearchExtraction> extractionCaptor = ArgumentCaptor.forClass(PlaceSearchExtraction.class);
        verify(aiSearchOrchestratorService)
                .resolveCandidates(extractionCaptor.capture(), eq(10.780000d), eq(106.700000d));

        PlaceSearchExtraction sent = extractionCaptor.getValue();
        assertEquals("Hanoi", sent.locationHint());
        assertTrue(sent.searchQueries().stream().noneMatch("ho chi minh city"::equalsIgnoreCase));
    }

    @Test
    void suggestPlacesFromQuery_DeniesUnauthorizedCallerBeforeAgentWork() {
        when(aiRequestGuardService.evaluateCurrentRequest()).thenReturn(
                AiRequestDecision.denied(
                        AiFailureCode.UNAUTHORIZED,
                        "Authentication is required for AI mutations."
                )
        );

        AiPlaceSuggestionResult result
                = aiOrchestratorService.suggestPlacesFromQuery("coffee");

        assertEquals(AiFailureCode.UNAUTHORIZED, result.failureCode());
        assertTrue(result.places().isEmpty());
        verify(placeSuggestionAgent, never()).extract("coffee");
    }

    @Test
    void draftTripFromQuery_RetriesWhenDraftUsesUnknownPlaceId() {
        String query = "build a day trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("coffee day trip"),
                List.of("coffee"),
                "cafe",
                null
        );
        Place place = place("p1", "Cafe");
        List<Place> candidates = List.of(place);
        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Bad Draft",
                null,
                List.of(new GeneratedStop("missing", 60, null, null))
        );
        GeneratedItinerary validDraft = new GeneratedItinerary(
                "Good Draft",
                "Nice route",
                List.of(new GeneratedStop("p1", 60, "Start here", "2026-01-01T09:00:00Z"))
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("unknown placeId"),
                any()
        )).thenReturn(validDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(1, result.candidatePlaces().size());
        assertEquals("coffee day trip", result.searchQueries().getFirst());
        assertEquals("p1", result.draft().stops().getFirst().placeId());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("Retried trip drafting")));
    }

    @Test
    void draftTripFromQuery_ReturnsFailureAfterRepeatedParseError() {
        String query = "make a trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("park day trip"),
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any()))
                .thenThrow(new AiParseException("bad draft"));
        when(tripDraftingAgent.retry(eq(query), eq(candidates), eq("bad draft"), any()))
                .thenThrow(new AiParseException("still bad"));

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.AI_PARSE_FAILURE, result.failureCode());
        assertTrue(result.warnings().contains("still bad"));
    }

    @Test
    void draftTripFromQuery_AutoRepairsDuplicatePlaceIdsWithoutRetry() {
        String query = "build a day trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("coffee stops"),
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
                        new GeneratedStop("p1", 60, null, "2026-01-01T09:00:00Z"),
                        new GeneratedStop("p1", 60, null, "2026-01-01T10:00:00Z")
                )
        );
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(invalidDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(2, result.draft().stops().size());
        assertEquals("p1", result.draft().stops().get(0).placeId());
        assertEquals("p2", result.draft().stops().get(1).placeId());
        verify(tripDraftingAgent, never()).retry(any(), any(), any(), any());
    }

    @Test
    void draftTripFromQuery_ReturnsValidationFailureAfterRepeatedDurationViolation() {
        String query = "make a trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("park trip"),
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Bad Draft",
                null,
                List.of(new GeneratedStop("p1", 5, null, null))
        );
        GeneratedItinerary invalidRetry = new GeneratedItinerary(
                "Still Bad",
                null,
                List.of(new GeneratedStop("p1", 500, null, null))
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("out-of-range durationMinutes"),
                any()
        )).thenReturn(invalidRetry);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.AI_VALIDATION_FAILURE, result.failureCode());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("out-of-range durationMinutes")));
    }

    @Test
    void draftTripFromQuery_AllowsHigherTotalDurationForMultiDayIntent() {
        String query = "Lap lich trinh trai nghiem 2 ng\u00e0y o Sai Gon";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("district 1 district 3 city walk"),
                List.of("district 1", "district 3"),
                null,
                null
        );

        Place first = place("p1", "Morning Market");
        Place second = place("p2", "Art Museum");
        Place third = place("p3", "Riverside Night Spot");
        List<Place> candidates = List.of(first, second, third);

        GeneratedItinerary draft = new GeneratedItinerary(
                "2 Day Discovery",
                null,
                List.of(
                        new GeneratedStop("p1", 300, "Morning", null),
                        new GeneratedStop("p2", 300, "Afternoon", null),
                        new GeneratedStop("p3", 300, "Evening", null)
                )
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(aiSearchOrchestratorService.hasLocationFocus(extraction)).thenReturn(false);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(draft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(3, result.draft().stops().size());
        verify(tripDraftingAgent, never()).retry(any(), any(), any(), any());
    }

    @Test
    void draftTripFromQuery_EnforcesTotalDurationLimitForSingleDayIntent() {
        String query = "Plan one day in Saigon";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("saigon highlights"),
                List.of("saigon"),
                null,
                null
        );

        Place first = place("p1", "Breakfast Spot");
        Place second = place("p2", "Museum");
        Place third = place("p3", "Dinner Venue");
        List<Place> candidates = List.of(first, second, third);

        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Overloaded Day",
                null,
                List.of(
                        new GeneratedStop("p1", 300, null, null),
                        new GeneratedStop("p2", 300, null, null),
                        new GeneratedStop("p3", 300, null, null)
                )
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(aiSearchOrchestratorService.hasLocationFocus(extraction)).thenReturn(false);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("total duration limit of 720 minutes (current=900)"),
                any()
        )).thenReturn(invalidDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.AI_VALIDATION_FAILURE, result.failureCode());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("total duration limit of 720 minutes (current=900)")));
    }

    @Test
    void draftTripFromQuery_ReturnsValidationFailureForInvalidPlannedDateTime() {
        String query = "make a timed trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("timed park route"),
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Bad Timing",
                null,
                List.of(new GeneratedStop("p1", 60, null, "not-a-datetime"))
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("invalid plannedDateTime"),
                any()
        )).thenReturn(invalidDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.AI_VALIDATION_FAILURE, result.failureCode());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("invalid plannedDateTime")));
    }

    @Test
    void draftTripFromQuery_ReturnsValidationFailureForNonMonotonicPlannedDateTime() {
        String query = "make a timed trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("timed cafe route"),
                List.of("cafe"),
                null,
                null
        );
        Place first = place("p1", "Cafe 1");
        Place second = place("p2", "Cafe 2");
        List<Place> candidates = List.of(first, second);
        GeneratedItinerary invalidDraft = new GeneratedItinerary(
                "Out of order",
                null,
                List.of(
                        new GeneratedStop("p1", 60, null, "2026-01-01T11:00:00Z"),
                        new GeneratedStop("p2", 60, null, "2026-01-01T10:00:00Z")
                )
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(invalidDraft);
        when(tripDraftingAgent.retry(
                eq(query),
                eq(candidates),
                contains("non-monotonic plannedDateTime"),
                any()
        )).thenReturn(invalidDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.AI_VALIDATION_FAILURE, result.failureCode());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("non-monotonic plannedDateTime")));
    }

    @Test
    void draftTripFromQuery_ReturnsSafeFailureWhenNoCandidatesExist() {
        String query = "make a trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("park trip"),
                List.of("park"),
                null,
                null
        );
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(List.of());

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertEquals(AiFailureCode.NO_CANDIDATE_PLACES, result.failureCode());
        assertTrue(result.candidatePlaces().isEmpty());
    }

    @Test
    void draftTripFromQuery_FallsBackToDefaultTitleWhenBlank() {
        String query = "plan a short trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("short park trip"),
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        GeneratedItinerary itinerary = new GeneratedItinerary(
                "   ",
                "Summary",
                List.of(new GeneratedStop("p1", 60, null, null))
        );
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(itinerary);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals("AI Trip Draft", result.draft().title());
    }

    @Test
    void draftTripFromQuery_FallsBackToGeneratedSummaryWhenBlank() {
        String query = "plan a short trip";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("short park trip"),
                List.of("park"),
                null,
                null
        );
        List<Place> candidates = List.of(place("p1", "Park"));
        GeneratedItinerary itinerary = new GeneratedItinerary(
                "Parks",
                "   ",
                List.of(new GeneratedStop("p1", 60, null, null))
        );
        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(itinerary);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(
                "A curated itinerary with 1 planned stop featuring Park.",
                result.draft().summary());
    }

    @Test
    void draftTripFromQuery_PrioritizesLocationFocusedCandidatesBeforeDrafting() {
        String query = "Food tour in Ho Chi Minh City, centered around District 1";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("food tour district 1 ho chi minh city"),
                List.of("food", "district 1", "ho chi minh city"),
                "food",
                null,
                "District 1, Ho Chi Minh City"
        );

        Place districtOneA = place("p1", "Ben Nghe Eatery");
        districtOneA.setAddress("Bến Nghé, Quận 1, Sài Gòn");

        Place districtOneB = place("p2", "Ben Thanh Bites");
        districtOneB.setAddress("District 1, HCMC");

        Place districtThree = place("p3", "City Lunch Stop");
        districtThree.setAddress("District 3, Ho Chi Minh City");

        Place thuDuc = place("p4", "Riverside Dinner");
        thuDuc.setAddress("Thu Duc, Ho Chi Minh City");

        List<Place> candidates = List.of(districtOneA, districtOneB, districtThree, thuDuc);
        List<Place> focusedCandidates = List.of(districtOneA, districtOneB);

        GeneratedItinerary focusedDraft = new GeneratedItinerary(
                "Good Focus",
                "Mostly District 1",
                List.of(
                        new GeneratedStop("p1", 60, "Breakfast", "2026-01-01T09:00:00Z"),
                        new GeneratedStop("p2", 60, "Lunch", "2026-01-01T11:00:00Z")
                )
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(aiSearchOrchestratorService.hasLocationFocus(extraction)).thenReturn(true);
        when(aiSearchOrchestratorService.matchesLocationFocus(extraction, districtOneA)).thenReturn(true);
        when(aiSearchOrchestratorService.matchesLocationFocus(extraction, districtOneB)).thenReturn(true);
        when(aiSearchOrchestratorService.matchesLocationFocus(extraction, districtThree)).thenReturn(false);
        when(aiSearchOrchestratorService.matchesLocationFocus(extraction, thuDuc)).thenReturn(false);
        when(tripDraftingAgent.draft(eq(query), eq(focusedCandidates), any())).thenReturn(focusedDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(2, result.candidatePlaces().size());
        assertEquals("p1", result.draft().stops().get(0).placeId());
        assertEquals("p2", result.draft().stops().get(1).placeId());
        assertTrue(result.warnings().stream().anyMatch(
                warning -> warning.contains("Prioritized candidates within the requested area")));
        verify(tripDraftingAgent, never()).retry(any(), any(), any(), any());
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

    @Test
    void draftTripFromQuery_ArrangesDateTimeAndBuildsNotesFromVietnameseHints() {
        String query = "Chuy\u1ebfn \u0111i 1 ng\u00e0y sau 1 tu\u1ea7n";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("hanoi one day"),
                List.of("hanoi"),
                null,
                "relax"
        );

        Place first = place("p1", "Lakeside Cafe");
        first.setTags(List.of("cafe", "coffee"));
        Place second = place("p2", "Temple of Literature");
        second.setTags(List.of("historic", "museum"));
        List<Place> candidates = List.of(first, second);

        GeneratedItinerary rawDraft = new GeneratedItinerary(
                "1 Day Plan",
                null,
                List.of(
                        new GeneratedStop("p1", 90, null, null),
                        new GeneratedStop("p2", 90, null, null)
                )
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(aiSearchOrchestratorService.hasLocationFocus(extraction)).thenReturn(false);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), org.mockito.ArgumentMatchers.any()))
                .thenReturn(rawDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(2, result.draft().stops().size());
        assertTrue(result.draft().stops().stream().allMatch(stop -> stop.note() != null && !stop.note().isBlank()));
        assertTrue(result.draft().stops().stream().allMatch(stop -> stop.plannedDateTime() != null));

        OffsetDateTime firstStop = OffsetDateTime.parse(result.draft().stops().get(0).plannedDateTime());
        OffsetDateTime secondStop = OffsetDateTime.parse(result.draft().stops().get(1).plannedDateTime());
        assertEquals(18, firstStop.getDayOfMonth());
        assertEquals(9, firstStop.getHour());
        assertTrue(secondStop.isAfter(firstStop));
    }

    @Test
    void draftTripFromQuery_UsesEnglishFallbackNotesForEnglishQuery() {
        String query = "Plan a coffee trip in Hanoi tomorrow morning";
        PlaceSearchExtraction extraction = new PlaceSearchExtraction(
                List.of("hanoi coffee route"),
                List.of("coffee", "hanoi"),
                null,
                "relax"
        );

        Place place = place("p1", "Lake Cafe");
        place.setTags(List.of("cafe", "coffee"));
        List<Place> candidates = List.of(place);

        GeneratedItinerary rawDraft = new GeneratedItinerary(
                "Coffee Day",
                null,
                List.of(new GeneratedStop("p1", 90, null, null))
        );

        when(placeSuggestionAgent.extract(query)).thenReturn(extraction);
        when(aiSearchOrchestratorService.resolveCandidates(extraction)).thenReturn(candidates);
        when(aiSearchOrchestratorService.hasLocationFocus(extraction)).thenReturn(false);
        when(tripDraftingAgent.draft(eq(query), eq(candidates), any())).thenReturn(rawDraft);

        AiTripDraftResult result = aiOrchestratorService.draftTripFromQuery(query);

        assertNull(result.failureCode());
        assertEquals(1, result.draft().stops().size());
        String note = result.draft().stops().getFirst().note();
        assertTrue(note != null && note.toLowerCase().contains("stop at"));
        assertTrue(note == null || !note.contains("Bu\u1ed5i"));
    }

    private Place place(String id, String name) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        return place;
    }
}
