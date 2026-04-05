package com.bif.app.feature.map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.MapState;
import com.bif.app.domain.model.OfflineMapDownloadState;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.Route;
import com.bif.app.domain.model.Review;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IRouteRepository;
import com.bif.app.domain.repository.IReviewRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class MapViewModel extends ViewModel {

    private static final String NO_MAP_DATA_DOWNLOADED = "No map data downloaded";
    private static final String OFFLINE_ENGINE_UNAVAILABLE = "Offline routing engine unavailable";
    private static final String ROUTE_ESTIMATING = "Estimating route...";

    private final IMapRepository mapRepository;
    private final IPlaceRepository placeRepository;
    private final IFavoriteRepository favoriteRepository;
    private final IGroupRepository groupRepository;
    private final IRouteRepository routeRepository;
    private final IReviewRepository reviewRepository;

    private final MutableLiveData<Event<String>> _statusText = new MutableLiveData<>();
    public final LiveData<Event<String>> statusText = _statusText;

    private final MutableLiveData<String> _routeSummary = new MutableLiveData<>(null);
    public final LiveData<String> routeSummary = _routeSummary;

    private final MutableLiveData<String> _routeGeometryJson = new MutableLiveData<>(null);
    public final LiveData<String> routeGeometryJson = _routeGeometryJson;

    private final MutableLiveData<RouteSession> _routeSession = new MutableLiveData<>(RouteSession.idle());
    public final LiveData<RouteSession> routeSession = _routeSession;

    private final MutableLiveData<String> locationSearchQuery = new MutableLiveData<>();
    public final LiveData<Location> searchResult;

    private final MutableLiveData<String> placesSearchQuery = new MutableLiveData<>();
    public final LiveData<List<Place>> searchResults;

    private final MutableLiveData<String> placesHistoryQuery = new MutableLiveData<>();
    public final LiveData<List<Place>> historySearchResults;

    private final MutableLiveData<String> _currentPlaceId = new MutableLiveData<>();
    public final LiveData<List<Review>> currentPlaceReviews;
    public final LiveData<Review> currentMyReview;

    private final MutableLiveData<Boolean> _isLoadingReviews = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoadingReviews = _isLoadingReviews;

    public final LiveData<List<Favorite>> allFavorites;
    public final LiveData<List<Group>> allGroups;
    public final LiveData<List<String>> searchHistory;

    private final java.util.concurrent.atomic.AtomicBoolean activeSearchPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Inject
    public MapViewModel(
            IMapRepository mapRepository,
            IPlaceRepository placeRepository,
            IFavoriteRepository favoriteRepository,
            IGroupRepository groupRepository,
            IRouteRepository routeRepository,
            IReviewRepository reviewRepository) {
        this.mapRepository = mapRepository;
        this.placeRepository = placeRepository;
        this.favoriteRepository = favoriteRepository;
        this.groupRepository = groupRepository;
        this.routeRepository = routeRepository;
        this.reviewRepository = reviewRepository;

        this.searchResult = Transformations.switchMap(locationSearchQuery, placeRepository::searchLocation);
        this.searchResults = Transformations.switchMap(placesSearchQuery, placeRepository::searchPlaces);
        this.historySearchResults = Transformations.switchMap(
                placesHistoryQuery, placeRepository::searchPlacesFromHistory);
        this.allFavorites = favoriteRepository.getAllFavorites();
        this.allGroups = groupRepository.getGroups();
        this.searchHistory = placeRepository.getSearchHistory();

        this.currentPlaceReviews = Transformations.switchMap(_currentPlaceId, reviewRepository::getReviewsForPlace);
        this.currentMyReview = Transformations.switchMap(_currentPlaceId, reviewRepository::getMyReview);
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

    public void loadReviews(Place place) {
        if (place == null || place.location == null) return;
        _isLoadingReviews.setValue(true);
        new Thread(() -> {
            String internalId = reviewRepository.resolveInternalPlaceId(
                place.placeSource, place.id, place.location.latitude, place.location.longitude, place.name);
            _currentPlaceId.postValue(internalId);
            reviewRepository.refreshReviews(internalId);
            // After resolving and triggering refresh, we can consider "initial setup" done. 
            // In a real app, refreshReviews callback would set this to false.
            _isLoadingReviews.postValue(false);
        }).start();
    }

    public void submitReview(int stars, String comment) {
        String placeId = _currentPlaceId.getValue();
        if (placeId != null) {
            reviewRepository.submitReview(placeId, stars, comment);
        }
    }

    public void removeFromFavorites(Favorite favorite) {
        favoriteRepository.deleteFavorite(favorite);
    }

    public void clearRouteSummary() {
        cancelRoute();
    }

    public void cancelRoute() {
        _routeSummary.setValue(null);
        _routeGeometryJson.setValue(null);
        _routeSession.setValue(RouteSession.idle());
    }

    public boolean hasActiveRouteSession() {
        RouteSession current = _routeSession.getValue();
        return current != null && current.isVisible();
    }

    @NonNull
    public RouteSession getCurrentRouteSession() {
        RouteSession current = _routeSession.getValue();
        return current != null ? current : RouteSession.idle();
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

    public void beginRoutePreview(@Nullable Place destinationPlace,
                                  @NonNull Location origin,
                                  @NonNull Location destinationLocation) {
        _routeSummary.setValue(ROUTE_ESTIMATING);
        _routeGeometryJson.setValue(null);
        _routeSession.setValue(RouteSession.loading(destinationPlace));
        requestRoute(destinationPlace, Arrays.asList(origin, destinationLocation));
    }

    public void estimateRoute(Location origin, Location destination) {
        if (origin == null || destination == null) {
            return;
        }
        beginRoutePreview(null, origin, destination);
    }

    public void startFollowingRoute() {
        RouteSession current = getCurrentRouteSession();
        if (!current.hasRoute()) {
            return;
        }
        _routeSession.setValue(current.withFollowing(true));
    }

    public void updateFollowingLocation(@Nullable Location location, float bearingDegrees) {
        RouteSession current = getCurrentRouteSession();
        if (!current.hasRoute()) {
            return;
        }
        _routeSession.setValue(current.withLocation(location, normalizeBearing(bearingDegrees)));
    }

    private void requestRoute(@Nullable Place destinationPlace,
                              @NonNull List<Location> waypoints) {
        final LiveData<Route> routeLiveData = routeRepository.getRoute(waypoints);

        Observer<Route> observer = new Observer<Route>() {
            @Override
            public void onChanged(Route route) {
                routeLiveData.removeObserver(this);

                if (route == null) {
                    String message = resolveNoRouteMessage();
                    _routeSummary.postValue(message);
                    _routeGeometryJson.postValue(null);
                    _routeSession.postValue(RouteSession.error(destinationPlace, message));
                    setStatusText(message);
                    return;
                }

                RouteSession readySession = buildReadySession(destinationPlace, route);
                _routeSummary.postValue(readySession.summaryText);
                _routeGeometryJson.postValue(route.getGeometryJson());
                _routeSession.postValue(readySession);
                setStatusText(readySession.summaryText);
            }
        };

        routeLiveData.observeForever(observer);
    }

    @NonNull
    private RouteSession buildReadySession(@Nullable Place destinationPlace, @NonNull Route route) {
        return RouteSession.ready(
                destinationPlace,
                route,
                formatRouteSummary(route),
                formatDurationText(route),
                formatDistanceText(route));
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

    private String formatRouteSummary(@NonNull Route route) {
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

    private String formatDurationText(@NonNull Route route) {
        long totalMinutes = Math.max(1L, Math.round(route.getDurationSeconds() / 60.0));
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours <= 0L) {
            return String.format(Locale.getDefault(), "%d min", minutes);
        }
        if (minutes == 0L) {
            return String.format(Locale.getDefault(), "%d hr", hours);
        }
        return String.format(Locale.getDefault(), "%d hr %d min", hours, minutes);
    }

    private String formatDistanceText(@NonNull Route route) {
        double distanceMeters = Math.max(0.0, route.getDistanceMeters());
        if (distanceMeters < 1000.0) {
            return String.format(Locale.getDefault(), "%.0f m", distanceMeters);
        }
        return String.format(Locale.getDefault(), "%.1f km", distanceMeters / 1000.0);
    }

    private float normalizeBearing(float bearingDegrees) {
        float normalized = bearingDegrees % 360f;
        if (normalized < 0f) {
            normalized += 360f;
        }
        return normalized;
    }
}
