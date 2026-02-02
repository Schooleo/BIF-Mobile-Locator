package com.bif.locator.domain.repository;

import com.bif.locator.domain.model.Location;
import androidx.lifecycle.LiveData;

public interface ILocationRepository {
    LiveData<Location> getCurrentLocation();
    LiveData<Location> searchLocation(String query);
    void requestLocationUpdates(LocationCallback callback);
    void removeLocationUpdates(LocationCallback callback);
    LiveData<java.util.List<com.bif.locator.domain.model.Place>> searchPlaces(String query);
}
