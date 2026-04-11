package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;

import java.util.List;

public interface PlaceSearchIndexSyncService {

    void upsert(Place place);

    void deleteById(String placeId);

    /**
     * Update only rating fields for a place in the search index.
     * Implementations must preserve all non-rating fields (true partial update semantics).
     */
    void updateRatingOnly(String placeId, double rating, int reviewCount);

    /** Create the backing collection/index if it does not already exist. */
    default void ensureCollectionExists() {
        // no-op for providers that don't need it
    }

    /**
     * Bulk-import a batch of places. Default falls back to individual upserts.
     * @return the number of successfully imported documents
     */
    default int batchUpsert(List<Place> places) {
        int count = 0;
        for (Place place : places) {
            upsert(place);
            count++;
        }
        return count;
    }
}