package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;

import java.util.List;

public interface PlaceSearchProvider {

    List<Place> search(String query);
}
