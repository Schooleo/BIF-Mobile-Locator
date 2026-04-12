package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.AiPlaceSuggestionResult;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;

import java.util.List;

public interface IPlaceRepository {
    LiveData<Location> searchLocation(String query);
    LiveData<List<Place>> searchPlaces(String query, Location userLocation);
    default LiveData<List<Place>> searchPlaces(String query,
            Location userLocation,
            boolean saveToHistory) {
        return searchPlaces(query, userLocation);
    }
    LiveData<List<Place>> searchPlacesFromHistory(String query);
    LiveData<AiPlaceSuggestionResult> suggestPlacesFromQuery(String query);
    default LiveData<AiPlaceSuggestionResult> suggestPlacesFromQuery(
            String query,
            Double latitude,
            Double longitude,
            String cityBias) {
        return suggestPlacesFromQuery(query);
    }
    void persistPlace(Place place, String action);
    LiveData<List<Place>> getAllPersistedPlaces();
    LiveData<List<String>> getSearchHistory();
}
