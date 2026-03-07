package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;

import java.util.List;

public interface IPlaceRepository {
    LiveData<Location> searchLocation(String query);
    LiveData<List<Place>> searchPlaces(String query);
}