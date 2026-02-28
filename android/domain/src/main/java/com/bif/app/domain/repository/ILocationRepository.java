package com.bif.app.domain.repository;

import com.bif.app.domain.model.Location;
import androidx.lifecycle.LiveData;

public interface ILocationRepository {
    LiveData<Location> getCurrentLocation();
    LiveData<Location> searchLocation(String query);
    void requestLocationUpdates(LocationCallback callback);
    void removeLocationUpdates(LocationCallback callback);
    LiveData<java.util.List<com.bif.app.domain.model.Place>> searchPlaces(String query);
}
