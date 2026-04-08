package com.bif.app.core.network;

import android.util.Log;

import com.apollographql.apollo.api.ApolloResponse;
import com.apollographql.java.client.ApolloClient;
import com.bif.app.core.network.dto.ai.AiPlaceSuggestionPayload;
import com.bif.app.core.network.dto.ai.AiSuggestedPlacePayload;
import com.bif.app.core.network.dto.ai.AiTripDraftPayload;
import com.bif.app.core.network.dto.ai.AiTripDraftResultPayload;
import com.bif.app.core.network.dto.ai.AiTripDraftStopPayload;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
public class AiGraphQlClient {

    private static final String TAG = "AiGraphQlClient";

    private final ApolloClient apolloClient;

    @Inject
    public AiGraphQlClient(ApolloClient apolloClient) {
        this.apolloClient = apolloClient;
    }

    public AiPlaceSuggestionPayload suggestPlacesFromQuery(String query) throws Exception {
        AtomicReference<ApolloResponse<SuggestPlacesFromQueryMutation.Data>> responseRef =
                new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        apolloClient
                .mutation(new SuggestPlacesFromQueryMutation(query))
                .enqueue(response -> {
                    responseRef.set(response);
                    latch.countDown();
                });

        boolean completed;
        try {
            completed = latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AiPlaceSuggestionPayload(new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
        }
        if (!completed) {
            return new AiPlaceSuggestionPayload(new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
        }

        ApolloResponse<SuggestPlacesFromQueryMutation.Data> response = responseRef.get();
        if (response == null) {
            return new AiPlaceSuggestionPayload(new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
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
            for (SuggestPlacesFromQueryMutation.Place place : payload.places) {
                if (place == null) {
                    continue;
                }

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
                        place.addedToTripCount != null ? place.addedToTripCount : 0,
                        latitude,
                        longitude
                ));
            }
        }

        return new AiPlaceSuggestionPayload(mapped, warnings, null);
    }

    public AiTripDraftResultPayload draftTripFromQuery(String query) throws Exception {
        AtomicReference<ApolloResponse<DraftTripFromQueryMutation.Data>> responseRef =
                new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        apolloClient
                .mutation(new DraftTripFromQueryMutation(query))
                .enqueue(response -> {
                    responseRef.set(response);
                    latch.countDown();
                });

        boolean completed = latch.await(15, TimeUnit.SECONDS);
        if (!completed) {
            return new AiTripDraftResultPayload(null, new ArrayList<>(), new ArrayList<>(), "AI_FAILURE");
        }

        ApolloResponse<DraftTripFromQueryMutation.Data> response = responseRef.get();
        if (response == null || response.data == null || response.data.draftTripFromQuery == null) {
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
