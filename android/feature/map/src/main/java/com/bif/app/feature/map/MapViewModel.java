package com.bif.app.feature.map;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;

import java.util.List;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MapViewModel extends ViewModel {

    private final IMapRepository mapRepository;
    private final IPlaceRepository placeRepository;

    private final MutableLiveData<String> _statusText = new MutableLiveData<>();
    public final LiveData<String> statusText = _statusText;

    private final MutableLiveData<String> locationSearchQuery = new MutableLiveData<>();
    public final LiveData<Location> searchResult;

    private final MutableLiveData<String> placesSearchQuery = new MutableLiveData<>();
    public final LiveData<List<Place>> searchResults;

    @Inject
    public MapViewModel(IMapRepository mapRepository, IPlaceRepository placeRepository) {
        this.mapRepository = mapRepository;
        this.placeRepository = placeRepository;

        this.searchResult = Transformations.switchMap(locationSearchQuery, placeRepository::searchLocation);

        this.searchResults = Transformations.switchMap(placesSearchQuery, placeRepository::searchPlaces);
    }

    public void setStatusText(String text) {
        _statusText.setValue(text);
    }

    public void searchLocation(String query) {
        locationSearchQuery.setValue(query);
    }

    public void searchForPlaces(String query) {
        placesSearchQuery.setValue(query);
    }

    public void saveMapState(double lat, double lng, float zoom) {
        mapRepository.saveMapState(new MapState(lat, lng, zoom));
    }

    public MapState getLastMapState() {
        return mapRepository.getMapState();
    }
}
