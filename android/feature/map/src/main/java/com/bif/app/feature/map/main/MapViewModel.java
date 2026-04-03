package com.bif.app.feature.map.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Route;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IRouteRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MapViewModel extends ViewModel {

    private static final String NO_MAP_DATA_DOWNLOADED = "No map data downloaded";
    private static final String OFFLINE_ENGINE_UNAVAILABLE = "Offline routing engine unavailable";

    private final IMapRepository mapRepository;
    private final IPlaceRepository placeRepository;
    private final IFavoriteRepository favoriteRepository;
    private final IGroupRepository groupRepository;
    private final IRouteRepository routeRepository;

    private final MutableLiveData<Event<String>> _statusText = new MutableLiveData<>();
    public final LiveData<Event<String>> statusText = _statusText;

    private final MutableLiveData<String> _routeSummary = new MutableLiveData<>(null);
    public final LiveData<String> routeSummary = _routeSummary;

    private final MutableLiveData<String> _routeGeometryJson = new MutableLiveData<>(null);
    public final LiveData<String> routeGeometryJson = _routeGeometryJson;

    private final MutableLiveData<String> locationSearchQuery = new MutableLiveData<>();
    public final LiveData<Location> searchResult;

    private final MutableLiveData<String> placesSearchQuery = new MutableLiveData<>();
    public final LiveData<List<Place>> searchResults;

    private final MutableLiveData<String> placesHistoryQuery = new MutableLiveData<>();
    public final LiveData<List<Place>> historySearchResults;

    public final LiveData<List<Favorite>> allFavorites;
    public final LiveData<List<Group>> allGroups;
    public final LiveData<List<String>> searchHistory;

    private final java.util.concurrent.atomic.AtomicBoolean activeSearchPending = new java.util.concurrent.atomic.AtomicBoolean(
            false);

    @Inject
    public MapViewModel(
            IMapRepository mapRepository,
            IPlaceRepository placeRepository,
            IFavoriteRepository favoriteRepository,
            IGroupRepository groupRepository,
            IRouteRepository routeRepository) {
        this.mapRepository = mapRepository;
        this.placeRepository = placeRepository;
        this.favoriteRepository = favoriteRepository;
        this.groupRepository = groupRepository;
        this.routeRepository = routeRepository;

        this.searchResult = Transformations.switchMap(locationSearchQuery, placeRepository::searchLocation);

        this.searchResults = Transformations.switchMap(
                placesSearchQuery, placeRepository::searchPlaces);

        this.historySearchResults = Transformations.switchMap(
                placesHistoryQuery, placeRepository::searchPlacesFromHistory);

        this.allFavorites = favoriteRepository.getAllFavorites();
        this.allGroups = groupRepository.getGroups();
        this.searchHistory = placeRepository.getSearchHistory();
    }

    public void setStatusText(String text) {
        _statusText.setValue(new Event<>(text));
    }

    public void searchLocation(String query) {
        locationSearchQuery.setValue(query);
    }

    public void searchForPlaces(String query) {
        activeSearchPending.set(true);
        placesSearchQuery.setValue(query);
    }

    public void searchForPlacesFromHistory(String query) {
        activeSearchPending.set(true);
        placesHistoryQuery.setValue(query);
    }

    public void notifySearchDone(int resultCount) {
        if (activeSearchPending.compareAndSet(true, false)) {
            if (resultCount > 0) {
                setStatusText("Found " + resultCount + " places.");
            } else {
                setStatusText("No places found.");
            }
        }
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
        placeRepository.persistPlace(place, "favorite");
    }

    public void cacheViewedPlace(Place place) {
        placeRepository.persistPlace(place, "viewed");
    }

    public void removeFromFavorites(Favorite favorite) {
        favoriteRepository.deleteFavorite(favorite);
    }

    public void clearRouteSummary() {
        _routeSummary.setValue(null);
        _routeGeometryJson.setValue(null);
    }

    public LiveData<Boolean> observeOnlineStatus() {
        return routeRepository.observeOnlineStatus();
    }

    public LiveData<OfflineMapDownloadState> observeOfflineMapDownloadState() {
        return routeRepository.observeOfflineCityMapDownloadState();
    }

    public void downloadOfflineCityMap(Location origin) {
        routeRepository.requestOfflineCityMapDownload(origin);
    }

    public void estimateRoute(Location origin, Location destination) {
        if (origin == null || destination == null) {
            return;
        }

        final LiveData<Route> routeLiveData = routeRepository.getRoute(
                Arrays.asList(origin, destination));

        Observer<Route> observer = new Observer<Route>() {
            @Override
            public void onChanged(Route route) {
                routeLiveData.removeObserver(this);

                if (route == null) {
                    String message = resolveNoRouteMessage();
                    _routeSummary.postValue(message);
                    _routeGeometryJson.postValue(null);
                    setStatusText(message);
                    return;
                }

                String summary = formatRouteSummary(route);
                _routeSummary.postValue(summary);
                _routeGeometryJson.postValue(route.getGeometryJson());
                setStatusText(summary);
            }
        };

        routeLiveData.observeForever(observer);
    }

    private String resolveNoRouteMessage() {
        LiveData<OfflineMapDownloadState> downloadStateLiveData = routeRepository.observeOfflineCityMapDownloadState();
        if (downloadStateLiveData == null) {
            return NO_MAP_DATA_DOWNLOADED;
        }

        OfflineMapDownloadState downloadState = downloadStateLiveData.getValue();
        if (downloadState == null) {
            return NO_MAP_DATA_DOWNLOADED;
        }

        if (downloadState.status == OfflineMapDownloadState.Status.COMPLETED
                || downloadState.status == OfflineMapDownloadState.Status.ALREADY_DOWNLOADED) {
            return OFFLINE_ENGINE_UNAVAILABLE;
        }

        return NO_MAP_DATA_DOWNLOADED;
    }

    private String formatRouteSummary(Route route) {
        double distanceKm = Math.max(0.0, route.getDistanceMeters()) / 1000.0;
        long durationMin = Math.max(1L, Math.round(route.getDurationSeconds() / 60.0));
        String sourceLabel;
        if (Route.SOURCE_ONLINE.equals(route.getSource())) {
            sourceLabel = "online";
        } else if (Route.SOURCE_BROUTER.equals(route.getSource())) {
            sourceLabel = "brouter";
        } else {
            sourceLabel = "offline";
        }

        return String.format(
                Locale.getDefault(),
                "Route %.1f km, ~%d min (%s)",
                distanceKm,
                durationMin,
                sourceLabel);
    }
}
