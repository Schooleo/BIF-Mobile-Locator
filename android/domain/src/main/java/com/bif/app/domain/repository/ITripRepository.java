package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import java.util.List;

public interface ITripRepository {
    LiveData<List<TripPlan>> getTripsByGroup(String groupId);
    void addStopToTrip(String tripId, TripStop stop);
    void refreshTrips(String groupId);
}
