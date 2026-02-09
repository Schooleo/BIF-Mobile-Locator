package com.bif.locator.ui.location;

import androidx.lifecycle.ViewModel;
import com.bif.locator.domain.repository.ILocationRepository;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.bif.locator.domain.model.Location;
import com.bif.locator.domain.repository.LocationCallback;

@HiltViewModel
public class LocationViewModel extends ViewModel {

    private final ILocationRepository repository;

    private final MutableLiveData<Location> _currentLocation = new MutableLiveData<>();
    public final LiveData<Location> currentLocation = _currentLocation;

    private final MutableLiveData<String> _statusText = new MutableLiveData<>();
    public final LiveData<String> statusText = _statusText;

    private final MutableLiveData<Boolean> _isTracking = new MutableLiveData<>(false);
    public final LiveData<Boolean> isTracking = _isTracking;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(Location location) {
            _currentLocation.postValue(location);
            _statusText.postValue("Location updated: " + 
                location.latitude + ", " + location.longitude);
        }

        @Override
        public void onError(String message) {
            _statusText.postValue("Error: " + message);
        }
    };

    @Inject
    public LocationViewModel(ILocationRepository repository) {
        this.repository = repository;
    }

    public void fetchCurrentLocation() {
        _statusText.setValue("Fetching location...");
        repository.requestLocationUpdates(locationCallback);
    }

    public void startTracking() {
        _statusText.setValue("Tracking location...");
        _isTracking.setValue(true);
        repository.requestLocationUpdates(locationCallback);
    }

    public void stopTracking() {
        _statusText.setValue("Tracking stopped");
        _isTracking.setValue(false);
        repository.removeLocationUpdates(locationCallback);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        repository.removeLocationUpdates(locationCallback);
    }
}
