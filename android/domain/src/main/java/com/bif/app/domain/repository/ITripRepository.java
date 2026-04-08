package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripMember;
import com.bif.app.domain.model.TripStop;
import java.util.List;

public interface ITripRepository {
    interface OperationCallback {
        void onComplete(boolean success);
    }

    LiveData<List<TripPlan>> getAllTrips();
    LiveData<TripPlan> getTripById(String tripId);

    default void createTrip(String title, String description, long startAt, long endAt) {
        createTrip(title, description, startAt, endAt, null);
    }

    default void updateTrip(String tripId, String title, String description, long startAt, long endAt) {
        updateTrip(tripId, title, description, startAt, endAt, null);
    }

    default void deleteTrip(String tripId) {
        deleteTrip(tripId, null);
    }

    void createTrip(String title,
                    String description,
                    long startAt,
                    long endAt,
                    OperationCallback callback);
    void updateTrip(String tripId,
                    String title,
                    String description,
                    long startAt,
                    long endAt,
                    OperationCallback callback);
    void deleteTrip(String tripId, OperationCallback callback);
    void saveDraftTrip(String tripId,
                       String groupId,
                       String title,
                       String description,
                       long startAt,
                       long endAt,
                       List<TripStop> stops,
                       OperationCallback callback);
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
