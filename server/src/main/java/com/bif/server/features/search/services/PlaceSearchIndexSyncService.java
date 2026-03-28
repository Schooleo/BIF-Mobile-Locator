package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;

public interface PlaceSearchIndexSyncService {

    void upsert(Place place);

    void deleteById(String placeId);
}