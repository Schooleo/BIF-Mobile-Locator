package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.repository.ITripRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class TripDetailViewModel extends ViewModel {

    private final ITripRepository tripRepository;
    private LiveData<TripPlan> trip;

    @Inject
    public TripDetailViewModel(ITripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    public void loadTrip(String tripId) {
        trip = tripRepository.getTripById(tripId);
    }

    public LiveData<TripPlan> getTrip() {
        return trip;
    }
}

