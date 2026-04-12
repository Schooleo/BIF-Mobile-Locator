package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import androidx.annotation.Nullable;

import com.bif.app.domain.model.AiPlaceSuggestionResult;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;

import java.util.List;

public interface IPlaceRepository {
    interface PersistenceCallback {
        void onSuccess();

        void onError(Throwable error);
    }

    LiveData<Location> searchLocation(String query);
    LiveData<List<Place>> searchPlaces(String query, Location userLocation);
    LiveData<List<Place>> searchPlacesFromHistory(String query);
    LiveData<Place> getPlaceById(String placeId);
    LiveData<AiPlaceSuggestionResult> suggestPlacesFromQuery(String query);
    void persistPlace(Place place, String action);
    void persistPlace(Place place, String action, @Nullable PersistenceCallback callback);
    LiveData<List<Place>> getAllPersistedPlaces();
    LiveData<List<String>> getSearchHistory();
}