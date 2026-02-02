package com.bif.locator.ui.map;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.locator.domain.repository.ILocationRepository;
import com.bif.locator.domain.repository.IMapRepository;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MapViewModel extends ViewModel {

    private final ILocationRepository locationRepository;
    private final IMapRepository mapRepository;
    private final MutableLiveData<String> _searchQuery = new MutableLiveData<>();
    public final LiveData<Location> searchResult;
    public final MutableLiveData<String> statusText = new MutableLiveData<>();

    @Inject
    public MapViewModel(ILocationRepository locationRepository, IMapRepository mapRepository) {
        this.locationRepository = locationRepository;
        this.mapRepository = mapRepository;
        this.searchResult = Transformations.switchMap(_searchQuery, locationRepository::searchLocation);
    }

    public void searchLocation(String query) {
        _searchQuery.setValue(query);
    }

    public void setStatusText(String text) {
        statusText.setValue(text);
    }
}
