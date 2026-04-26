package com.bif.server.features.ai.services;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

@Service
public class AiOrchestratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiOrchestratorService.class);
    private static final ZoneId DEFAULT_AI_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PlaceSuggestionAgent placeSuggestionAgent;
    private final TripDraftingAgent tripDraftingAgent;
    private final AiSearchOrchestratorService aiSearchOrchestratorService;
    private final AiRequestGuardService aiRequestGuardService;
    private final OllamaProperties ollamaProperties;
    private final TripScheduleHintExtractor tripScheduleHintExtractor;
    private final VibeHintNormalizer vibeHintNormalizer;

    private static final int TRAVEL_BUFFER_MINUTES = 30;

    public AiOrchestratorService(
            PlaceSuggestionAgent placeSuggestionAgent,
            TripDraftingAgent tripDraftingAgent,
            AiSearchOrchestratorService aiSearchOrchestratorService,
            AiRequestGuardService aiRequestGuardService,
            OllamaProperties ollamaProperties,
            TripScheduleHintExtractor tripScheduleHintExtractor,
            VibeHintNormalizer vibeHintNormalizer) {
        this.placeSuggestionAgent = placeSuggestionAgent;
        this.tripDraftingAgent = tripDraftingAgent;
        this.aiSearchOrchestratorService = aiSearchOrchestratorService;
        this.aiRequestGuardService = aiRequestGuardService;
        this.ollamaProperties = ollamaProperties;
        this.tripScheduleHintExtractor = tripScheduleHintExtractor;
        this.vibeHintNormalizer = vibeHintNormalizer;
    }

    public AiPlaceSuggestionResult suggestPlacesFromQuery(String query) {
        return suggestPlacesFromQuery(query, null, null, null);
    }

    public AiPlaceSuggestionResult suggestPlacesFromQuery(
            String query,
            Double latitude,
            Double longitude,
            String cityBias) {
        AiRequestDecision decision = aiRequestGuardService.evaluateCurrentRequest();
        if (!decision.allowed()) {
            logDeniedRequest("suggestPlacesFromQuery", decision, query);
            return deniedSuggestion(decision);
        }
        if (query == null || query.isBlank()) {
            LOGGER.warn("AI suggest request rejected: invalid blank query");
            return new AiPlaceSuggestionResult(
                    List.of(),
                    List.of(),
                    null,
                    null,
                    List.of(),
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
            PlaceSearchExtraction enrichedExtraction = applySuggestionBias(
                    extraction,
                    cityBias,
                    latitude,
                    longitude);

            List<Place> places = aiSearchOrchestratorService.resolveCandidates(
                    enrichedExtraction,
                    latitude,
                    longitude);
            if (places.isEmpty()) {
                warnings.add("No grounded places matched the extracted parameters.");
                LOGGER.warn(
                        "AI suggest returned no grounded results for query='{}' locationHint='{}' searchQueries={} cityBias='{}'",
                        safeQuerySnippet(query),
                        enrichedExtraction.locationHint(),
                        enrichedExtraction.searchQueries(),
                        cityBias);
            }
            return new AiPlaceSuggestionResult(
                    places,
                    enrichedExtraction.keywords(),
                    enrichedExtraction.category(),
                    enrichedExtraction.vibe(),
                    enrichedExtraction.searchQueries(),
                    enrichedExtraction.locationHint(),
                    warnings,
                    places.isEmpty() ? AiFailureCode.NO_RESULTS : null);
        } catch (AiIntegrationException e) {
            warnings.add(e.getMessage());
            AiFailureCode failureCode = failureCodeFor(e);
            logAiFailure("suggestPlacesFromQuery", failureCode, query, e);
            return new AiPlaceSuggestionResult(
                    List.of(),
                    List.of(),
                    null,
                    null,
                    List.of(),
                    null,
                    warnings,
                    failureCode);
        }
    }

    public AiTripDraftResult draftTripFromQuery(String query) {
        AiRequestDecision decision = aiRequestGuardService.evaluateCurrentRequest();
        if (!decision.allowed()) {
            logDeniedRequest("draftTripFromQuery", decision, query);
            return deniedDraft(decision);
        }
        if (query == null || query.isBlank()) {
            LOGGER.warn("AI draft request rejected: invalid blank query");
            return new AiTripDraftResult(
                    null,
                    List.of(),
                    List.of(),
                    List.of("Query must not be blank."),
                    AiFailureCode.INVALID_QUERY);
        }

        List<String> warnings = new ArrayList<>();
        List<Place> candidatePlaces = List.of();
        List<String> searchQueries = List.of();
        TripScheduleHintExtractor.TripScheduleHints scheduleHints
                = tripScheduleHintExtractor.extract(query);
        AiGenerationConstraints constraints = buildGenerationConstraints(query, scheduleHints);
        try {
            PlaceSearchExtraction extraction = withRetry(
                    () -> placeSuggestionAgent.extract(query),
                    reason -> placeSuggestionAgent.retry(query, reason),
                    "place suggestion",
                    warnings);
            searchQueries = extraction.searchQueries().isEmpty()
                    ? extraction.keywords()
                    : extraction.searchQueries();
            candidatePlaces = resolveContextAwareCandidates(extraction, constraints, warnings);
            if (candidatePlaces.isEmpty()) {
                warnings.add("No candidate places were available for drafting.");
                LOGGER.warn(
                        "AI draft failed with no candidate places for query='{}' locationHint='{}' searchQueries={}",
                        safeQuerySnippet(query),
                        extraction.locationHint(),
                        searchQueries);
                return new AiTripDraftResult(
                        null,
                        List.of(),
                        searchQueries,
                        warnings,
                        AiFailureCode.NO_CANDIDATE_PLACES);
            }
            List<Place> resolvedCandidatePlaces = prioritizeDraftCandidates(
                    extraction,
                    candidatePlaces,
                    warnings);
            candidatePlaces = resolvedCandidatePlaces;

            GeneratedItinerary itinerary = withRetry(
                    () -> validateGeneratedItinerary(
                            enrichGeneratedItinerary(
                                    tripDraftingAgent.draft(
                                            query,
                                            resolvedCandidatePlaces,
                                            scheduleHints.promptDirective()),
                                    resolvedCandidatePlaces,
                                    extraction,
                                    scheduleHints,
                                    query),
                            resolvedCandidatePlaces,
                            extraction,
                            scheduleHints),
                    reason -> validateGeneratedItinerary(
                            enrichGeneratedItinerary(
                                    tripDraftingAgent.retry(
                                            query,
                                            resolvedCandidatePlaces,
                                            reason,
                                            scheduleHints.promptDirective()),
                                    resolvedCandidatePlaces,
                                    extraction,
                                    scheduleHints,
                                    query),
                            resolvedCandidatePlaces,
                            extraction,
                            scheduleHints),
                    "trip drafting",
                    warnings);

            return new AiTripDraftResult(
                    mapDraft(itinerary, resolvedCandidatePlaces),
                    resolvedCandidatePlaces,
                    searchQueries,
                    warnings,
                    null);
        } catch (AiIntegrationException e) {
            warnings.add(e.getMessage());
            AiFailureCode failureCode = failureCodeFor(e);
            logAiFailure("draftTripFromQuery", failureCode, query, e);
            return new AiTripDraftResult(
                    null,
                    candidatePlaces,
                    searchQueries,
                    warnings,
                    failureCode);
        }
    }

    private void logDeniedRequest(String operation, AiRequestDecision decision, String query) {
        LOGGER.warn(
                "AI {} denied with code={} message='{}' query='{}'",
                operation,
                decision.failureCode(),
                decision.message(),
                safeQuerySnippet(query));
    }

    private void logAiFailure(
            String operation,
            AiFailureCode failureCode,
            String query,
            AiIntegrationException exception) {
        String querySnippet = safeQuerySnippet(query);
        String reason = exception.getMessage();

        if (failureCode == AiFailureCode.AI_PARSE_FAILURE
                || failureCode == AiFailureCode.AI_VALIDATION_FAILURE) {
            LOGGER.warn(
                    "AI {} failed with code={} query='{}' reason='{}'",
                    operation,
                    failureCode,
                    querySnippet,
                    reason,
                    exception);
            return;
        }

        LOGGER.error(
                "AI {} failed with code={} query='{}' reason='{}'",
                operation,
                failureCode,
                querySnippet,
                reason,
                exception);
    }

    private String safeQuerySnippet(String query) {
        if (query == null) {
            return "<null>";
        }
        String normalized = query.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 120) {
            return normalized;
        }
        return normalized.substring(0, 117) + "...";
    }

    private List<Place> prioritizeDraftCandidates(
            PlaceSearchExtraction extraction,
            List<Place> candidates,
            List<String> warnings) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (extraction == null || !aiSearchOrchestratorService.hasLocationFocus(extraction)) {
            return candidates;
        }

        List<Place> focusedCandidates = candidates.stream()
                .filter(place -> aiSearchOrchestratorService.matchesLocationFocus(extraction, place))
                .toList();

        if (focusedCandidates.isEmpty()) {
            return candidates;
        }
        if (focusedCandidates.size() < candidates.size()) {
            warnings.add("Prioritized candidates within the requested area for drafting.");
        }
        return focusedCandidates;
    }


    private AiGenerationConstraints buildGenerationConstraints(
            String query,
            TripScheduleHintExtractor.TripScheduleHints scheduleHints) {
        LocalTime preferredStartTime = scheduleHints == null ? null : scheduleHints.preferredStartTime();
        LocalTime preferredEndTime = scheduleHints == null ? null : scheduleHints.preferredEndTime();
        List<String> targetVibes = vibeHintNormalizer.extractTargetVibes(query);
        return AiGenerationConstraints.of(preferredStartTime, preferredEndTime, targetVibes);
    }

    private List<Place> resolveContextAwareCandidates(
            PlaceSearchExtraction extraction,
            AiGenerationConstraints constraints,
            List<String> warnings) {
        if (constraints == null || !constraints.hasTargetVibes()) {
            List<Place> candidates = aiSearchOrchestratorService.resolveCandidates(extraction);
            return candidates == null ? List.of() : candidates;
        }

        List<Place> constrainedCandidates = aiSearchOrchestratorService.resolveCandidates(extraction, constraints);
        if (constrainedCandidates == null) {
            constrainedCandidates = List.of();
        }
        if (!constrainedCandidates.isEmpty()) {
            warnings.add("Applied schedule/vibe context before drafting candidates.");
            return constrainedCandidates;
        }

        List<Place> fallbackCandidates = aiSearchOrchestratorService.resolveCandidates(extraction);
        if (fallbackCandidates == null) {
            fallbackCandidates = List.of();
        }
        if (!fallbackCandidates.isEmpty()) {
            warnings.add("Relaxed AI hint constraints because the context-aware search returned no places.");
        }
        return fallbackCandidates;
    }

    private AiPlaceSuggestionResult deniedSuggestion(AiRequestDecision decision) {
        return new AiPlaceSuggestionResult(
                List.of(),
                List.of(),
                null,
                null,
                List.of(),
                null,
                List.of(decision.message()),
                decision.failureCode());
    }

    private PlaceSearchExtraction applySuggestionBias(
            PlaceSearchExtraction extraction,
            String cityBias,
            Double latitude,
            Double longitude) {
        if (extraction == null) {
            return new PlaceSearchExtraction(List.of(), List.of(), null, null, normalizeBias(cityBias));
        }

        String normalizedCityBias = normalizeBias(cityBias);
        String coordinateCityBias = coordinateLocationHint(latitude, longitude);
        String locationHint = extraction.locationHint();
        boolean locationHintBackedByTerms = isLocationHintBackedByTerms(extraction, locationHint);

        String effectiveCityBias = normalizedCityBias;
        if (effectiveCityBias == null
                && coordinateCityBias != null
                && (locationHint == null || !locationHintBackedByTerms)) {
            effectiveCityBias = coordinateCityBias;
        }

        if (effectiveCityBias != null
                && (locationHint == null || !locationHintBackedByTerms)) {
            locationHint = effectiveCityBias;
        }

        List<String> searchQueries = new ArrayList<>(extraction.searchQueries());
        if (searchQueries.isEmpty()) {
            searchQueries.addAll(extraction.keywords());
        }
        if (effectiveCityBias != null
                && searchQueries.stream().noneMatch(effectiveCityBias::equalsIgnoreCase)) {
            searchQueries.add(0, effectiveCityBias);
        }
        String coordinateQuery = coordinateLocationHint(latitude, longitude);
        if (coordinateQuery != null
                && searchQueries.stream().noneMatch(coordinateQuery::equalsIgnoreCase)) {
            searchQueries.add(0, coordinateQuery);
        }

        return new PlaceSearchExtraction(
                searchQueries,
                extraction.keywords(),
                extraction.category(),
                extraction.vibe(),
                locationHint);
    }

    private String normalizeBias(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private boolean isLocationHintBackedByTerms(
            PlaceSearchExtraction extraction,
            String locationHint) {
        if (locationHint == null || locationHint.isBlank() || extraction == null) {
            return false;
        }

        String normalizedHint = normalizeForComparison(locationHint);
        if (normalizedHint.isBlank()) {
            return false;
        }

        for (String query : extraction.searchQueries()) {
            if (normalizeForComparison(query).contains(normalizedHint)) {
                return true;
            }
        }
        for (String keyword : extraction.keywords()) {
            if (normalizeForComparison(keyword).contains(normalizedHint)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForComparison(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String coordinateLocationHint(Double latitude, Double longitude) {
        if (!isValidCoordinates(latitude, longitude)) {
            return null;
        }
        return String.format(Locale.ROOT, "near %.6f, %.6f", latitude, longitude);
    }

    private boolean isValidCoordinates(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90d
                && latitude <= 90d
                && longitude >= -180d
                && longitude <= 180d;
    }

    private AiTripDraftResult deniedDraft(AiRequestDecision decision) {
        return new AiTripDraftResult(
                null,
                List.of(),
                List.of(),
                List.of(decision.message()),
                decision.failureCode());
    }

    private GeneratedItinerary validateGeneratedItinerary(
            GeneratedItinerary itinerary,
            List<Place> candidatePlaces,
            PlaceSearchExtraction extraction,
            TripScheduleHintExtractor.TripScheduleHints scheduleHints) {
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
        int allowedTotalDurationMinutes = resolveAllowedTotalDurationMinutes(itinerary, scheduleHints);
        if (totalDurationMinutes > allowedTotalDurationMinutes) {
            throw new AiValidationException(
                    "AI draft exceeded total duration limit of "
                    + allowedTotalDurationMinutes
                    + " minutes (current="
                    + totalDurationMinutes
                    + ").");
        }

        OffsetDateTime previous = null;
        for (GeneratedStop stop : itinerary.stops()) {
            if (stop.plannedDateTime() == null) {
                continue;
            }

            OffsetDateTime current = parseDateTimeQuietly(stop.plannedDateTime());
            if (current == null) {
                throw new AiValidationException("AI draft included an invalid plannedDateTime value.");
            }

            if (previous != null && current.isBefore(previous)) {
                throw new AiValidationException(
                        "AI draft included non-monotonic plannedDateTime values.");
            }
            previous = current;
        }

        validateLocationFocus(itinerary, candidatePlaces, extraction, placeById);

        return itinerary;
    }

    private int resolveAllowedTotalDurationMinutes(
            GeneratedItinerary itinerary,
            TripScheduleHintExtractor.TripScheduleHints scheduleHints) {
        int daySpanFromHints = scheduleHints == null ? 0 : scheduleHints.daySpan();
        int daySpanFromDraftedDateTime = estimateDaySpanFromPlannedDateTimes(itinerary);
        int inferredDaySpan = Math.max(1, Math.max(daySpanFromHints, daySpanFromDraftedDateTime));
        return AiGenerationConstraints.MAX_TOTAL_DURATION_MINUTES * inferredDaySpan;
    }

    private int estimateDaySpanFromPlannedDateTimes(GeneratedItinerary itinerary) {
        if (itinerary == null || itinerary.stops() == null || itinerary.stops().isEmpty()) {
            return 0;
        }

        OffsetDateTime min = null;
        OffsetDateTime max = null;
        for (GeneratedStop stop : itinerary.stops()) {
            OffsetDateTime parsed = parseDateTimeQuietly(stop.plannedDateTime());
            if (parsed == null) {
                continue;
            }
            if (min == null || parsed.isBefore(min)) {
                min = parsed;
            }
            if (max == null || parsed.isAfter(max)) {
                max = parsed;
            }
        }

        if (min == null || max == null) {
            return 0;
        }

        long daysBetween = ChronoUnit.DAYS.between(min.toLocalDate(), max.toLocalDate()) + 1;
        return (int) Math.max(1, daysBetween);
    }

    private GeneratedItinerary enrichGeneratedItinerary(
            GeneratedItinerary itinerary,
            List<Place> candidatePlaces,
            PlaceSearchExtraction extraction,
            TripScheduleHintExtractor.TripScheduleHints scheduleHints,
            String userQuery) {
        if (itinerary == null || itinerary.stops().isEmpty()) {
            return itinerary;
        }

        Map<String, Place> placeById = indexPlaces(candidatePlaces);
        boolean shouldArrangeDateTime = scheduleHints.shouldArrangeDateTime();
        List<GeneratedStop> normalizedStops = normalizeDuplicatePlaceIds(
                itinerary.stops(),
                placeById);

        List<GeneratedStop> stops = enrichStops(
                normalizedStops,
                placeById,
                extraction,
                scheduleHints,
                shouldArrangeDateTime,
                userQuery);
        return new GeneratedItinerary(itinerary.title(), itinerary.summary(), stops);
    }

    private List<GeneratedStop> normalizeDuplicatePlaceIds(
            List<GeneratedStop> stops,
            Map<String, Place> placeById) {
        if (stops == null || stops.isEmpty() || placeById.isEmpty()) {
            return stops;
        }

        List<String> candidateIds = new ArrayList<>(placeById.keySet());
        Set<String> usedPlaceIds = new HashSet<>();
        List<GeneratedStop> normalizedStops = new ArrayList<>(stops.size());
        boolean rewritten = false;

        for (GeneratedStop stop : stops) {
            String placeId = stop.placeId();
            if (usedPlaceIds.add(placeId)) {
                normalizedStops.add(stop);
                continue;
            }

            String replacementPlaceId = firstUnusedCandidateId(candidateIds, usedPlaceIds);
            if (replacementPlaceId == null) {
                normalizedStops.add(stop);
                continue;
            }

            usedPlaceIds.add(replacementPlaceId);
            rewritten = true;
            LOGGER.warn(
                    "AI draft duplicate placeId '{}' rewritten to grounded candidate '{}'",
                    placeId,
                    replacementPlaceId);
            normalizedStops.add(new GeneratedStop(
                    replacementPlaceId,
                    stop.durationMinutes(),
                    stop.startTime(),
                    stop.endTime(),
                    stop.duration(),
                    stop.note(),
                    stop.plannedDateTime()));
        }

        return rewritten ? normalizedStops : stops;
    }

    private String firstUnusedCandidateId(List<String> candidateIds, Set<String> usedPlaceIds) {
        for (String candidateId : candidateIds) {
            if (!usedPlaceIds.contains(candidateId)) {
                return candidateId;
            }
        }
        return null;
    }

    private List<GeneratedStop> enrichStops(
            List<GeneratedStop> stops,
            Map<String, Place> placeById,
            PlaceSearchExtraction extraction,
            TripScheduleHintExtractor.TripScheduleHints scheduleHints,
            boolean shouldArrangeDateTime,
            String userQuery) {
        if (stops == null || stops.isEmpty()) {
            return List.of();
        }

        OffsetDateTime initialStart = resolveInitialStart(stops, scheduleHints, shouldArrangeDateTime);
        int daySpan = Math.max(1, scheduleHints.daySpan());
        int stopsPerDay = Math.max(1, (int) Math.ceil((double) stops.size() / daySpan));
        OffsetDateTime[] dayCursor = initializeDayCursor(initialStart, daySpan);
        NoteLanguage noteLanguage = resolveNoteLanguage(userQuery, extraction);
        LocalTime fallbackStopTime = scheduleHints.preferredStartTime() != null
                ? scheduleHints.preferredStartTime()
                : LocalTime.of(9, 0);

        List<GeneratedStop> enriched = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            GeneratedStop stop = stops.get(i);
            Place place = placeById.get(stop.placeId());

            OffsetDateTime plannedDateTime = null;
            String outputPlannedDateTime = stop.plannedDateTime();
            if (shouldArrangeDateTime && dayCursor.length > 0) {
                int dayIndex = Math.min(dayCursor.length - 1, i / stopsPerDay);
                plannedDateTime = dayCursor[dayIndex];
                int durationMinutes = stop.durationMinutes() == null ? 60 : stop.durationMinutes();
                dayCursor[dayIndex] = plannedDateTime.plusMinutes(durationMinutes + TRAVEL_BUFFER_MINUTES);
                outputPlannedDateTime = plannedDateTime.toString();
            } else if (stop.plannedDateTime() != null) {
                plannedDateTime = parseDateTimeQuietly(stop.plannedDateTime());
                if (plannedDateTime != null) {
                    outputPlannedDateTime = plannedDateTime.toString();
                }
            }

            String note = enrichStopNote(
                    stop.note(),
                    place,
                    extraction,
                    plannedDateTime,
                    noteLanguage);

            TimeFields timeFields = resolveTimeFields(stop, plannedDateTime, fallbackStopTime);
            fallbackStopTime = parseLocalTimeQuietly(timeFields.endTime()) != null
                    ? parseLocalTimeQuietly(timeFields.endTime()).plusMinutes(TRAVEL_BUFFER_MINUTES)
                    : fallbackStopTime.plusMinutes(timeFields.durationMinutes() + TRAVEL_BUFFER_MINUTES);
            enriched.add(new GeneratedStop(
                    stop.placeId(),
                    timeFields.durationMinutes(),
                    timeFields.startTime(),
                    timeFields.endTime(),
                    timeFields.durationMinutes(),
                    note,
                    outputPlannedDateTime));
        }

        return enriched;
    }

    private OffsetDateTime resolveInitialStart(
            List<GeneratedStop> stops,
            TripScheduleHintExtractor.TripScheduleHints scheduleHints,
            boolean shouldArrangeDateTime) {
        if (!shouldArrangeDateTime) {
            return null;
        }

        for (GeneratedStop stop : stops) {
            OffsetDateTime parsed = parseDateTimeQuietly(stop.plannedDateTime());
            if (parsed != null) {
                return parsed;
            }
        }

        return scheduleHints.suggestedStartDateTime();
    }

    private OffsetDateTime[] initializeDayCursor(OffsetDateTime initialStart, int daySpan) {
        if (initialStart == null) {
            return new OffsetDateTime[0];
        }

        OffsetDateTime[] cursor = new OffsetDateTime[daySpan];
        for (int i = 0; i < daySpan; i++) {
            cursor[i] = initialStart.plusDays(i).withSecond(0).withNano(0);
        }
        return cursor;
    }


    private TimeFields resolveTimeFields(
            GeneratedStop stop,
            OffsetDateTime plannedDateTime,
            LocalTime fallbackStartTime) {
        int durationMinutes = stop.durationMinutes() != null
                ? stop.durationMinutes()
                : (stop.duration() != null ? stop.duration() : 60);
        String startTime = normalizeClockTime(stop.startTime());
        String endTime = normalizeClockTime(stop.endTime());
        if (plannedDateTime != null) {
            startTime = plannedDateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES).toString();
            endTime = plannedDateTime.plusMinutes(durationMinutes)
                    .toLocalTime()
                    .truncatedTo(ChronoUnit.MINUTES)
                    .toString();
        }
        if (startTime == null && fallbackStartTime != null) {
            startTime = fallbackStartTime.truncatedTo(ChronoUnit.MINUTES).toString();
        }
        if (startTime != null && endTime == null) {
            LocalTime parsed = parseLocalTimeQuietly(startTime);
            if (parsed != null) {
                endTime = parsed.plusMinutes(durationMinutes).toString();
            }
        }
        return new TimeFields(durationMinutes, startTime, endTime);
    }

    private String normalizeClockTime(String value) {
        LocalTime parsed = parseLocalTimeQuietly(value);
        return parsed == null ? null : parsed.truncatedTo(ChronoUnit.MINUTES).toString();
    }

    private LocalTime parseLocalTimeQuietly(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private OffsetDateTime parseDateTimeQuietly(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();

        try {
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // Fall through to permissive parsing.
        }

        try {
            return Instant.parse(normalized).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            // Fall through to permissive parsing.
        }

        String normalizedWithT = normalized.contains("T")
                ? normalized
                : normalized.replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalizedWithT)
                    .atZone(DEFAULT_AI_TIME_ZONE)
                    .toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            // Fall through to date-only parsing.
        }

        try {
            return LocalDate.parse(normalized)
                    .atStartOfDay(DEFAULT_AI_TIME_ZONE)
                    .toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String enrichStopNote(
            String currentNote,
            Place place,
            PlaceSearchExtraction extraction,
            OffsetDateTime plannedDateTime,
            NoteLanguage noteLanguage) {
        if (currentNote != null && !currentNote.isBlank() && currentNote.trim().length() >= 10) {
            return currentNote;
        }

        String placeName = place != null && place.getName() != null && !place.getName().isBlank()
                ? place.getName().trim()
                : noteLanguage == NoteLanguage.VI ? "\u0111i\u1ec3m d\u1eebng" : "this stop";

        String prefix = timeWindowPrefix(plannedDateTime, noteLanguage);
        String activity = suggestActivity(place, extraction, noteLanguage);
        String note = noteLanguage == NoteLanguage.VI
                ? (prefix + " gh\u00e9 " + placeName + " \u0111\u1ec3 " + activity + ".").trim()
                : (prefix + " stop at " + placeName + " to " + activity + ".").trim();

        if (note.length() > 140) {
            note = note.substring(0, 139).trim() + "\u2026";
        }
        return note;
    }

    private String timeWindowPrefix(OffsetDateTime plannedDateTime, NoteLanguage noteLanguage) {
        if (plannedDateTime == null) {
            return "";
        }
        int hour = plannedDateTime.getHour();
        if (hour < 11) {
            return noteLanguage == NoteLanguage.VI ? "Bu\u1ed5i s\u00e1ng," : "In the morning,";
        }
        if (hour < 14) {
            return noteLanguage == NoteLanguage.VI ? "Bu\u1ed5i tr\u01b0a," : "Around noon,";
        }
        if (hour < 18) {
            return noteLanguage == NoteLanguage.VI ? "Bu\u1ed5i chi\u1ec1u," : "In the afternoon,";
        }
        return noteLanguage == NoteLanguage.VI ? "Bu\u1ed5i t\u1ed1i," : "In the evening,";
    }

    private String suggestActivity(
            Place place,
            PlaceSearchExtraction extraction,
            NoteLanguage noteLanguage) {
        List<String> tags = place == null || place.getTags() == null
                ? List.of()
                : place.getTags();

        String loweredTags = String.join(" ", tags).toLowerCase(Locale.ROOT);
        if (loweredTags.contains("cafe") || loweredTags.contains("coffee")) {
            return noteLanguage == NoteLanguage.VI
                    ? "th\u01b0 gi\u00e3n v\u00e0 th\u01b0\u1edfng th\u1ee9c c\u00e0 ph\u00ea"
                    : "relax and enjoy coffee";
        }
        if (loweredTags.contains("restaurant") || loweredTags.contains("food") || loweredTags.contains("eat")) {
            return noteLanguage == NoteLanguage.VI
                    ? "th\u01b0\u1edfng th\u1ee9c \u1ea9m th\u1ef1c \u0111\u1ecba ph\u01b0\u01a1ng"
                    : "taste local food";
        }
        if (loweredTags.contains("museum") || loweredTags.contains("history") || loweredTags.contains("historic")) {
            return noteLanguage == NoteLanguage.VI
                    ? "tham quan v\u00e0 t\u00ecm hi\u1ec3u v\u0103n h\u00f3a"
                    : "explore local history and culture";
        }
        if (loweredTags.contains("park") || loweredTags.contains("beach") || loweredTags.contains("nature")) {
            return noteLanguage == NoteLanguage.VI
                    ? "th\u01b0 gi\u00e3n v\u00e0 ng\u1eafm c\u1ea3nh"
                    : "unwind and enjoy the scenery";
        }

        String vibe = extraction == null || extraction.vibe() == null
                ? ""
                : extraction.vibe().toLowerCase(Locale.ROOT);
        if (vibe.contains("romantic") || vibe.contains("l\u00e3ng m\u1ea1n")) {
            return noteLanguage == NoteLanguage.VI
                    ? "t\u1eadn h\u01b0\u1edfng kh\u00f4ng gian l\u00e3ng m\u1ea1n"
                    : "enjoy a romantic atmosphere";
        }
        if (vibe.contains("quiet") || vibe.contains("y\u00ean t\u0129nh") || vibe.contains("relax")) {
            return noteLanguage == NoteLanguage.VI
                    ? "th\u01b0 gi\u00e3n v\u00e0 n\u1ea1p l\u1ea1i n\u0103ng l\u01b0\u1ee3ng"
                    : "relax and recharge";
        }
        return noteLanguage == NoteLanguage.VI
                ? "kh\u00e1m ph\u00e1 \u0111i\u1ec3m \u0111\u1ebfn n\u1ed5i b\u1eadt"
                : "discover a standout local spot";
    }

    private NoteLanguage resolveNoteLanguage(String userQuery, PlaceSearchExtraction extraction) {
        if (containsVietnameseSignals(userQuery)) {
            return NoteLanguage.VI;
        }
        if (extraction != null) {
            if (containsVietnameseSignals(extraction.vibe())
                    || containsVietnameseSignals(extraction.locationHint())) {
                return NoteLanguage.VI;
            }
            for (String query : extraction.searchQueries()) {
                if (containsVietnameseSignals(query)) {
                    return NoteLanguage.VI;
                }
            }
            for (String keyword : extraction.keywords()) {
                if (containsVietnameseSignals(keyword)) {
                    return NoteLanguage.VI;
                }
            }
        }
        return NoteLanguage.EN;
    }

    private boolean containsVietnameseSignals(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.matches(".*[đĐăĂâÂêÊôÔơƠưƯ].*");
    }

    private record TimeFields(int durationMinutes, String startTime, String endTime) {
    }

    private enum NoteLanguage {
        VI,
        EN
    }

    private void validateLocationFocus(
            GeneratedItinerary itinerary,
            List<Place> candidatePlaces,
            PlaceSearchExtraction extraction,
            Map<String, Place> placeById) {
        if (extraction == null || !aiSearchOrchestratorService.hasLocationFocus(extraction)) {
            return;
        }

        int availableFocusedCandidates = (int) candidatePlaces.stream()
                .filter(place -> aiSearchOrchestratorService.matchesLocationFocus(extraction, place))
                .count();
        if (availableFocusedCandidates == 0) {
            return;
        }

        int focusedStops = (int) itinerary.stops().stream()
                .map(GeneratedStop::placeId)
                .map(placeById::get)
                .filter(place -> aiSearchOrchestratorService.matchesLocationFocus(extraction, place))
                .count();

        int requiredFocusedStops = Math.min(
                availableFocusedCandidates,
                itinerary.stops().size() / 2 + 1);
        if (focusedStops >= requiredFocusedStops) {
            return;
        }

        throw new AiValidationException(
                "AI draft drifted away from the requested location focus ("
                + aiSearchOrchestratorService.describeLocationFocus(extraction)
                + "). Keep most stops in that area when grounded options are available.");
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
                stop.startTime(),
                stop.endTime(),
                stop.duration(),
                stop.note(),
                stop.plannedDateTime()))
                .toList();

        return new AiTripDraft(
                safeDraftTitle(itinerary.title()),
                safeDraftSummary(itinerary.summary(), stops),
                stops);
    }

    private String safeDraftTitle(String title) {
        if (title == null || title.isBlank()) {
            return "AI Trip Draft";
        }
        return title;
    }

    private String safeDraftSummary(String summary, List<AiTripDraftStop> stops) {
        if (summary != null && !summary.isBlank()) {
            return summary;
        }

        int stopCount = stops == null ? 0 : stops.size();
        if (stopCount <= 0) {
            return "A personalized AI itinerary with curated stops.";
        }

        String stopPhrase = stopCount == 1
                ? "1 planned stop"
                : String.format(Locale.ROOT, "%d planned stops", stopCount);

        List<String> featuredPlaces = stops.stream()
                .map(AiTripDraftStop::place)
                .filter(place -> place != null && place.getName() != null && !place.getName().isBlank())
                .map(place -> place.getName().trim())
                .distinct()
                .limit(2)
                .toList();

        if (featuredPlaces.isEmpty()) {
            return "A curated itinerary with " + stopPhrase + ".";
        }
        if (featuredPlaces.size() == 1) {
            return "A curated itinerary with " + stopPhrase
                    + " featuring " + featuredPlaces.get(0) + ".";
        }
        return "A curated itinerary with " + stopPhrase
                + " featuring " + featuredPlaces.get(0)
                + " and " + featuredPlaces.get(1) + ".";
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
                LOGGER.warn(
                        "AI {} retry {}/{} after {} failure: {}",
                        context,
                        attempt + 1,
                        ollamaProperties.getRetryCount(),
                        lastFailure.getClass().getSimpleName(),
                        lastFailure.getMessage());
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
