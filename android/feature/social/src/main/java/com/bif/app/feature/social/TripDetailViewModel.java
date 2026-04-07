package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class TripDetailViewModel extends ViewModel {

    private final ITripRepository tripRepository;
    private LiveData<TripPlan> trip;
    private String currentTripId = "";

    @Inject
    public TripDetailViewModel(ITripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    public void loadTrip(String tripId) {
        currentTripId = tripId == null ? "" : tripId;
        trip = tripRepository.getTripById(tripId);
    }

    public LiveData<TripPlan> getTrip() {
        return trip;
    }

    public String getCurrentTripId() {
        return currentTripId;
    }

    public void removeStop(String stopId) {
        if (currentTripId == null || currentTripId.trim().isEmpty()
                || stopId == null || stopId.trim().isEmpty()) {
            return;
        }
        tripRepository.removeStopFromTrip(currentTripId, stopId);
    }

    public void updateStopSchedule(TripStop stop, long scheduledAtMillis) {
        if (currentTripId == null || currentTripId.trim().isEmpty() || stop == null) {
            return;
        }
        TripPlan currentTrip = trip == null ? null : trip.getValue();
        if (currentTrip == null || currentTrip.getStops() == null || currentTrip.getStops().isEmpty()) {
            return;
        }

        List<TripStop> updatedStops = new ArrayList<>();
        for (TripStop item : currentTrip.getStops()) {
            if (item != null && stop.getId().equals(item.getId())) {
                updatedStops.add(new TripStop(
                        item.getId(),
                        item.getTitle(),
                        item.getNote(),
                        item.getPhotoUrl(),
                        item.getLocalImagePath(),
                        item.getLatitude(),
                        item.getLongitude(),
                        scheduledAtMillis,
                        scheduledAtMillis,
                        item.getOrderIndex()));
            } else {
                updatedStops.add(item);
            }
        }

        tripRepository.rearrangeStopsInTrip(currentTripId, updatedStops);
    }
}

