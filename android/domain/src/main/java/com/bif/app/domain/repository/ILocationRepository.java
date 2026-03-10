package com.bif.app.domain.repository;

import com.bif.app.domain.model.Location;
import androidx.lifecycle.LiveData;

public interface ILocationRepository {
    LiveData<Location> getCurrentLocation();
    void requestLocationUpdates(LocationCallback callback);
    void removeLocationUpdates(LocationCallback callback);
}
