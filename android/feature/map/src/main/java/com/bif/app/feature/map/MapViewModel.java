package com.bif.app.feature.map;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;

import java.util.List;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MapViewModel extends ViewModel {

    private final IMapRepository mapRepository;
    private final IPlaceRepository placeRepository;
    private final IFavoriteRepository favoriteRepository;

    private final MutableLiveData<String> _statusText = new MutableLiveData<>();
    public final LiveData<String> statusText = _statusText;

    private final MutableLiveData<String> locationSearchQuery = new MutableLiveData<>();
    public final LiveData<Location> searchResult;

    private final MutableLiveData<String> placesSearchQuery = new MutableLiveData<>();
    public final LiveData<List<Place>> searchResults;

    public final LiveData<List<Favorite>> allFavorites;

    @Inject
    public MapViewModel(
            IMapRepository mapRepository,
            IPlaceRepository placeRepository,
            IFavoriteRepository favoriteRepository
    ) {
        this.mapRepository = mapRepository;
        this.placeRepository = placeRepository;
        this.favoriteRepository = favoriteRepository;

        this.searchResult = Transformations.switchMap(locationSearchQuery, placeRepository::searchLocation);

        this.searchResults = Transformations.switchMap(placesSearchQuery, placeRepository::searchPlaces);

        this.allFavorites = favoriteRepository.getAllFavorites();
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

    public void addToFavorites(Place place) {
        Favorite favorite = new Favorite();

        favorite.name = place.name;
        favorite.address = place.address;
        favorite.rating = (int) place.rating;
        favorite.description = "";
        favorite.notes = "";
        favorite.imagePath = "";

        if (place.location != null) {
            favorite.latitude = place.location.latitude;
            favorite.longitude = place.location.longitude;
        }

        favoriteRepository.addFavorite(favorite);
    }

    public void removeFromFavorites(Favorite favorite) {
        favoriteRepository.deleteFavorite(favorite);
    }
}
