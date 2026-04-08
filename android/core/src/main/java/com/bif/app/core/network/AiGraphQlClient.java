package com.bif.app.core.network;

import android.util.Log;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.java.client.ApolloClient;
import com.apollographql.java.client.ApolloDisposable;
import com.bif.app.core.network.dto.ai.AiPlaceSuggestionPayload;
import com.bif.app.core.network.dto.ai.AiSuggestedPlacePayload;
import com.bif.app.core.network.dto.ai.AiTripDraftPayload;
import com.bif.app.core.network.dto.ai.AiTripDraftResultPayload;
import com.bif.app.core.network.dto.ai.AiTripDraftStopPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AiGraphQlClient {

    private static final String TAG = "AiGraphQlClient";
    private static final long REQUEST_TIMEOUT_SECONDS = 15L;

    private final ApolloClient apolloClient;
    private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

    @Inject
    public AiGraphQlClient(ApolloClient apolloClient) {
        this.apolloClient = apolloClient;
    }

    public CompletableFuture<AiPlaceSuggestionPayload> suggestPlacesFromQuery(String query) {
        CompletableFuture<AiPlaceSuggestionPayload> future = new CompletableFuture<>();
        final ApolloDisposable disposable;

        try {
            disposable = apolloClient
                    .mutation(new SuggestPlacesFromQueryMutation(query))
                    .enqueue(response -> {
                        if (future.isDone()) {
                            return;
                        }
                        future.complete(mapSuggestPlacesResponse(response));
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }

        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            if (future.completeExceptionally(new TimeoutException("suggestPlacesFromQuery timed out"))) {
                disposable.dispose();
            }
        }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        future.whenComplete((ignored, error) -> timeoutTask.cancel(false));
        return future;
    }

    public CompletableFuture<AiTripDraftResultPayload> draftTripFromQuery(String query) {
        CompletableFuture<AiTripDraftResultPayload> future = new CompletableFuture<>();
        final ApolloDisposable disposable;

        try {
            disposable = apolloClient
                    .mutation(new DraftTripFromQueryMutation(query))
                    .enqueue(response -> {
                        if (future.isDone()) {
                            return;
                        }
                        future.complete(mapDraftTripResponse(response));
                    });
        } catch (Exception e) {
            future.completeExceptionally(e);
            return future;
        }

        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            if (future.completeExceptionally(new TimeoutException("draftTripFromQuery timed out"))) {
                disposable.dispose();
            }
        }, REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        future.whenComplete((ignored, error) -> timeoutTask.cancel(false));
        return future;
    }

    private AiPlaceSuggestionPayload mapSuggestPlacesResponse(
            ApolloResponse<SuggestPlacesFromQueryMutation.Data> response) {
        if (response == null) {
            return new AiPlaceSuggestionPayload(new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
        }

        if (response.exception != null) {
            Log.w(TAG, "AI suggest transport failure", response.exception);
            ArrayList<String> warnings = new ArrayList<>();
            warnings.add("Transport error: " + response.exception.getMessage());
            return new AiPlaceSuggestionPayload(new ArrayList<>(), warnings, "AI_FAILURE");
        }

        if (response.errors != null && !response.errors.isEmpty()) {
            ArrayList<String> warnings = new ArrayList<>();
            for (Object error : response.errors) {
                warnings.add(String.valueOf(error));
            }
            Log.w(TAG, "AI suggest GraphQL errors=" + warnings);
            return new AiPlaceSuggestionPayload(new ArrayList<>(), warnings, "AI_FAILURE");
        }

        SuggestPlacesFromQueryMutation.Data data = response.data;
        if (data == null || data.suggestPlacesFromQuery == null) {
            return new AiPlaceSuggestionPayload(new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
        }

        SuggestPlacesFromQueryMutation.SuggestPlacesFromQuery payload = data.suggestPlacesFromQuery;

        List<String> warnings = payload.warnings != null
                ? new ArrayList<>(payload.warnings)
                : new ArrayList<>();
        if (!warnings.isEmpty()) {
            Log.d(TAG, "AI suggest warnings=" + warnings);
        }

        String failureCode = payload.failureCode != null
                ? payload.failureCode.rawValue
                : null;

        if (failureCode != null) {
            return new AiPlaceSuggestionPayload(new ArrayList<>(), warnings, failureCode);
        }

        List<AiSuggestedPlacePayload> mapped = new ArrayList<>();
        if (payload.places != null) {
            for (SuggestPlacesFromQueryMutation.Place suggestion : payload.places) {
                if (suggestion == null || suggestion.placeData == null) {
                    continue;
                }
                SuggestPlacesFromQueryMutation.PlaceData place = suggestion.placeData;

                Double latitude = place.location != null
                        ? place.location.latitude
                        : null;
                Double longitude = place.location != null
                        ? place.location.longitude
                        : null;

                mapped.add(new AiSuggestedPlacePayload(
                        place.id,
                        place.name,
                        place.address,
                        place.rating != null ? place.rating : 0d,
                    suggestion.addedToTripCount != null ? suggestion.addedToTripCount : 0,
                        latitude,
                        longitude
                ));
            }
        }
        return new AiPlaceSuggestionPayload(mapped, warnings, null);
    }

    private AiTripDraftResultPayload mapDraftTripResponse(
            ApolloResponse<DraftTripFromQueryMutation.Data> response) {
        if (response == null) {
            return new AiTripDraftResultPayload(null, new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
        }

        if (response.exception != null) {
            Log.w(TAG, "AI draft transport failure", response.exception);
            ArrayList<String> warnings = new ArrayList<>();
            warnings.add("Transport error: " + response.exception.getMessage());
            return new AiTripDraftResultPayload(null, new ArrayList<>(), warnings, "AI_FAILURE");
        }

        if (response.errors != null && !response.errors.isEmpty()) {
            ArrayList<String> warnings = new ArrayList<>();
            for (Object error : response.errors) {
                warnings.add(String.valueOf(error));
            }
            Log.w(TAG, "AI draft GraphQL errors=" + warnings);
            return new AiTripDraftResultPayload(null, new ArrayList<>(), warnings, "AI_FAILURE");
        }

        if (response.data == null || response.data.draftTripFromQuery == null) {
            return new AiTripDraftResultPayload(null, new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
        }

        DraftTripFromQueryMutation.DraftTripFromQuery payload = response.data.draftTripFromQuery;
        List<String> warnings = payload.warnings != null
                ? new ArrayList<>(payload.warnings)
                : new ArrayList<>();
        String failureCode = payload.failureCode != null
                ? payload.failureCode.rawValue
                : null;

        if (failureCode != null) {
            return new AiTripDraftResultPayload(null, new ArrayList<>(), warnings, failureCode);
        }

        List<AiSuggestedPlacePayload> candidatePlaces = new ArrayList<>();
        if (payload.candidatePlaces != null) {
            for (DraftTripFromQueryMutation.CandidatePlace place : payload.candidatePlaces) {
                if (place == null) {
                    continue;
                }
                candidatePlaces.add(mapPlace(place.id, place.name, place.address, place.rating,
                        place.location != null ? place.location.latitude : null,
                        place.location != null ? place.location.longitude : null));
            }
        }

        AiTripDraftPayload draft = null;
        if (payload.draft != null) {
            List<AiTripDraftStopPayload> stops = new ArrayList<>();
            if (payload.draft.stops != null) {
                for (DraftTripFromQueryMutation.Stop stop : payload.draft.stops) {
                    if (stop == null) {
                        continue;
                    }

                    AiSuggestedPlacePayload stopPlace = null;
                    if (stop.place != null) {
                        stopPlace = mapPlace(stop.place.id, stop.place.name, stop.place.address,
                                stop.place.rating,
                                stop.place.location != null ? stop.place.location.latitude : null,
                                stop.place.location != null ? stop.place.location.longitude : null);
                    }

                    stops.add(new AiTripDraftStopPayload(
                            stop.placeId,
                            stopPlace,
                            stop.durationMinutes != null ? stop.durationMinutes : 0,
                            stop.note
                    ));
                }
            }

            draft = new AiTripDraftPayload(
                    payload.draft.title,
                    payload.draft.summary,
                    stops
            );
        }

        return new AiTripDraftResultPayload(draft, candidatePlaces, warnings, null);
    }

    private AiSuggestedPlacePayload mapPlace(String id,
                                             String name,
                                             String address,
                                             Double rating,
                                             Double latitude,
                                             Double longitude) {
        return new AiSuggestedPlacePayload(
                id,
                name,
                address,
                rating != null ? rating : 0d,
                0,
                latitude,
                longitude
        );
    }
}
