package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripMember;
import com.bif.app.domain.model.TripStop;
import java.util.List;

public interface ITripRepository {
    LiveData<List<TripPlan>> getAllTrips();
    LiveData<TripPlan> getTripById(String tripId);
    void createTrip(String title, String description, long startAt, long endAt);
    LiveData<List<TripPlan>> getTripsByGroup(String groupId);
    LiveData<List<TripMember>> getTripMembers(String tripId);
    void addStopToTrip(String tripId, TripStop stop);
    void stageStopImageUpload(String tripId, String stopId, String localImagePath);
    void removeStopFromTrip(String tripId, String stopId);
    void addCollaborator(String tripId,
                         String userId,
                         String name,
                         String avatarLetter,
                         int avatarColor);
    void removeCollaborator(String tripId, String userId);
    void rearrangeStopsInTrip(String tripId, List<TripStop> newStops);
    void refreshTrips(String groupId);
}
