package com.bif.app.feature.map;

import android.util.Log;

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
import com.bif.app.domain.model.PlaceIdentityContext;
import com.bif.app.domain.model.Route;
import com.bif.app.domain.model.Review;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.domain.repository.IMapRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IRouteRepository;
import com.bif.app.domain.repository.IReviewRepository;
import com.bif.app.domain.repository.IPlaceRepository.PersistenceCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;


@HiltViewModel
public class MapViewModel extends ViewModel {
    private static final String TAG = "MapViewModel";
    private static final String NO_MAP_DATA_DOWNLOADED = "No map data downloaded";
    private static final String OFFLINE_ENGINE_UNAVAILABLE = "Offline routing engine unavailable";
    private static final String ROUTE_ESTIMATING = "Estimating route...";
    private static final double FOLLOWING_LOCATION_MIN_DELTA_METERS = 1.0d;
    private static final float FOLLOWING_BEARING_MIN_DELTA_DEGREES = 2.0f;

    private final IMapRepository mapRepository;
    private final IPlaceRepository placeRepository;
    private final IFavoriteRepository favoriteRepository;
    private final IGroupRepository groupRepository;
    private final IRouteRepository routeRepository;
    private final IReviewRepository reviewRepository;
    private final Executor reviewExecutor;

    public interface AddFavoriteCallback {
        void onSuccess();

        void onError(@NonNull String message);
    }

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

    public final LiveData<List<Place>> searchResults;
    private final MutableLiveData<SearchTrigger> searchTrigger = new MutableLiveData<>();

    private final MutableLiveData<Boolean> _isSearchingPlaces = new MutableLiveData<>(false);
    public final LiveData<Boolean> isSearchingPlaces = _isSearchingPlaces;

    @Nullable
    private String lastSearchQuery = null;

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

    @Nullable
    private Location currentSearchUserLocation;
    @Nullable
    private android.os.Handler searchHandler;
    @Nullable
    private Runnable searchRunnable;
    @Nullable
    private String pendingSearchQuery;
    private boolean pendingSearchSaveToHistory = true;
    private final long searchDebounceMs;

    private final java.util.concurrent.atomic.AtomicBoolean activeSearchPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        private final java.util.concurrent.atomic.AtomicLong reviewLoadRequestId =
            new java.util.concurrent.atomic.AtomicLong(0L);

    @Nullable
    private volatile PlaceIdentityContext currentPlaceIdentityContext;

    @Inject
    public MapViewModel(
            IMapRepository mapRepository,
            IPlaceRepository placeRepository,
            IFavoriteRepository favoriteRepository,
            IGroupRepository groupRepository,
            IRouteRepository routeRepository,
            IReviewRepository reviewRepository,
            Executor reviewExecutor) {
        this(mapRepository,
                placeRepository,
                favoriteRepository,
                groupRepository,
                routeRepository,
                reviewRepository,
                reviewExecutor,
                null,
                400L);
    }

    MapViewModel(
            IMapRepository mapRepository,
            IPlaceRepository placeRepository,
            IFavoriteRepository favoriteRepository,
            IGroupRepository groupRepository,
            IRouteRepository routeRepository,
            IReviewRepository reviewRepository,
            Executor reviewExecutor,
            @Nullable android.os.Handler injectedSearchHandler,
            long searchDebounceMs) {
        this.mapRepository = mapRepository;
        this.placeRepository = placeRepository;
        this.favoriteRepository = favoriteRepository;
        this.groupRepository = groupRepository;
        this.routeRepository = routeRepository;
        this.reviewRepository = reviewRepository;
        this.reviewExecutor = reviewExecutor;
        this.searchDebounceMs = searchDebounceMs;

        this.searchResult = Transformations.switchMap(locationSearchQuery, placeRepository::searchLocation);
        this.searchResults = Transformations.switchMap(searchTrigger, trigger -> {
            if (trigger == null || trigger.query.isEmpty()) {
                MutableLiveData<List<Place>> empty = new MutableLiveData<>();
                empty.setValue(new ArrayList<>());
                _isSearchingPlaces.postValue(false);
                return empty;
            }

            LiveData<List<Place>> source = placeRepository.searchPlaces(
                    trigger.query,
                    cloneLocation(trigger.userLocation),
                    trigger.saveToHistory);
            if (source == null) {
                MutableLiveData<List<Place>> empty = new MutableLiveData<>();
                empty.setValue(new ArrayList<>());
                _isSearchingPlaces.postValue(false);
                return empty;
            }

            return Transformations.map(source, places -> {
                _isSearchingPlaces.postValue(false);
                return places != null ? places : Collections.emptyList();
            });
        });

        this.historySearchResults = Transformations.switchMap(
                placesHistoryQuery,
                query -> Transformations.map(
                        placeRepository.searchPlacesFromHistory(query),
                        places -> {
                            _isSearchingPlaces.postValue(false);
                            return places != null ? places : Collections.emptyList();
                        }));
        this.allFavorites = favoriteRepository.getAllFavorites();
        this.allGroups = groupRepository.getGroups();
        this.searchHistory = placeRepository.getSearchHistory();

        this.currentPlaceReviews = Transformations.switchMap(_currentPlaceId, reviewRepository::getReviewsForPlace);
        this.currentMyReview = Transformations.switchMap(_currentPlaceId, reviewRepository::getMyReview);

        if (injectedSearchHandler != null) {
            this.searchHandler = injectedSearchHandler;
        } else {
            try {
                this.searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
            } catch (Exception ignored) {
                this.searchHandler = null;
            }
        }
    }

    public void setStatusText(String text) {
        _statusText.setValue(new Event<>(text));
    }

    public void clearPendingStatusText() {
        _statusText.setValue(null);
    }

    public void searchLocation(String query) {
        locationSearchQuery.setValue(query);
    }

    public void searchForPlaces(String query) {
        queueSearch(query, true);
    }

    public void searchForPlacesLive(String query) {
        queueSearch(query, false);
    }

    private void queueSearch(String query, boolean saveToHistory) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            if (searchHandler != null && searchRunnable != null) {
                searchHandler.removeCallbacks(searchRunnable);
            }
            pendingSearchQuery = null;
            pendingSearchSaveToHistory = true;
            lastSearchQuery = null;
            _isSearchingPlaces.setValue(false);
            activeSearchPending.set(false);
            searchTrigger.setValue(SearchTrigger.empty());
            return;
        }

        activeSearchPending.set(true);
        _isSearchingPlaces.setValue(true);
        pendingSearchQuery = normalized;
        pendingSearchSaveToHistory = saveToHistory;
 
        if (searchHandler == null) {
            dispatchSearchQuery(normalized, saveToHistory);
            return;
        }
 
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        searchRunnable = () -> {
            String queryToDispatch = pendingSearchQuery;
            if (queryToDispatch != null) {
                dispatchSearchQuery(queryToDispatch, pendingSearchSaveToHistory);
            }
        };
        searchHandler.postDelayed(searchRunnable, searchDebounceMs);
    }

    public void updateSearchUserLocation(@Nullable Location userLocation) {
        currentSearchUserLocation = cloneLocation(userLocation);
    }

    public void searchForPlacesFromHistory(String query) {
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        pendingSearchQuery = null;
        activeSearchPending.set(true);
        _isSearchingPlaces.setValue(true);
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
        addToFavorites(place, null);
    }

    public void addToFavorites(Place place, @Nullable AddFavoriteCallback callback) {
        if (place == null) {
            return;
        }

        reviewExecutor.execute(() -> {
            try {
                Favorite favorite = new Favorite();
                favorite.placeId = resolveCanonicalFavoritePlaceId(place);
                favorite.externalSource = hasText(place.placeSource) ? place.placeSource.trim() : null;
                favorite.externalId = hasText(place.id) ? place.id.trim() : null;
                favorite.placeName = hasText(place.name) ? place.name.trim() : null;
                favorite.name = place.name;
                favorite.address = place.address;
                favorite.rating = (int) place.rating;
                favorite.description = "";
                favorite.notes = "";
                if (place.location != null) {
                    favorite.latitude = place.location.latitude;
                    favorite.longitude = place.location.longitude;
                }

                favoriteRepository.addFavorite(favorite);
                placeRepository.persistPlace(place, "favorite", new PersistenceCallback() {
                    @Override
                    public void onSuccess() {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (callback != null) {
                            String message = error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Unable to add favorite";
                            callback.onError(message);
                        }
                    }
                });
            } catch (RuntimeException ex) {
                if (callback != null) {
                    String message = ex.getMessage() != null ? ex.getMessage() : "Unable to add favorite";
                    callback.onError(message);
                }
            }
        });
    }

    public void cacheViewedPlace(Place place) {
        placeRepository.persistPlace(place, "viewed");
    }

    public void clearReviewTargetForPreview() {
        reviewLoadRequestId.incrementAndGet();
        _currentPlaceId.setValue(null);
        _isLoadingReviews.setValue(false);
        updateCurrentReviewMetadata(null);
        Log.d(TAG, "clearReviewTargetForPreview: preview selection, skip resolve/reviews");
    }

    public void loadReviews(Place place) {
        if (place == null || place.location == null) {
            return;
        }

        if (place.isPreviewSelection()) {
            clearReviewTargetForPreview();
            Log.d(TAG, "loadReviews skipped for preview selection. id=" + place.id);
            return;
        }

        boolean canResolveWithMetadata = place.canResolveCanonicalIdentity();

        Log.d(TAG, "loadReviews: id=" + place.id
                + ", name=" + place.name
                + ", placeSource=" + place.placeSource
                + ", lat=" + place.location.latitude
                + ", lng=" + place.location.longitude
                + ", canResolveWithMetadata=" + canResolveWithMetadata);

        final PlaceIdentityContext placeIdentityContext = new PlaceIdentityContext();
        placeIdentityContext.externalSource = canResolveWithMetadata ? place.placeSource : null;
        placeIdentityContext.externalId = canResolveWithMetadata ? place.id : null;
        placeIdentityContext.lat = place.location != null ? place.location.latitude : null;
        placeIdentityContext.lng = place.location != null ? place.location.longitude : null;
        placeIdentityContext.placeName = place.name;

        final long requestId = reviewLoadRequestId.incrementAndGet();
        _isLoadingReviews.setValue(true);
        _currentPlaceId.setValue(null);
        reviewExecutor.execute(() -> {
            String internalId;
            if (canResolveWithMetadata) {
                internalId = reviewRepository.resolveInternalPlaceId(
                        placeIdentityContext.externalSource,
                        placeIdentityContext.externalId,
                        placeIdentityContext.lat,
                        placeIdentityContext.lng,
                        placeIdentityContext.placeName);
            } else {
                internalId = place.id != null ? place.id.trim() : null;
            }

            Log.d(TAG, "loadReviews resolved internalId=" + internalId
                    + " for placeId=" + place.id
                    + ", request canResolveWithMetadata=" + canResolveWithMetadata);

            if (requestId != reviewLoadRequestId.get()) {
                return;
            }

            if (internalId == null || internalId.trim().isEmpty()) {
                _isLoadingReviews.postValue(false);
                return;
            }

            updateCurrentReviewMetadata(placeIdentityContext);
            _currentPlaceId.postValue(internalId);
            reviewRepository.refreshReviews(internalId, () -> {
                if (requestId == reviewLoadRequestId.get()) {
                    _isLoadingReviews.postValue(false);
                }
            });
        });
    }

    private String resolveCanonicalFavoritePlaceId(@NonNull Place place) {
        String fallbackPlaceId = place.id != null ? place.id.trim() : "";

        Log.d(TAG, "resolveCanonicalFavoritePlaceId: id=" + place.id
            + ", name=" + place.name
            + ", placeSource=" + place.placeSource
            + ", lat=" + (place.location != null ? place.location.latitude : null)
            + ", lng=" + (place.location != null ? place.location.longitude : null));

        if (!place.canResolveCanonicalIdentity()) {
            return fallbackPlaceId;
        }

        String resolved = reviewRepository.resolveInternalPlaceId(
                place.placeSource,
                place.id,
                place.location.latitude,
                place.location.longitude,
                place.name);

        Log.d(TAG, "resolveCanonicalFavoritePlaceId resolved=" + resolved
            + " fallback=" + fallbackPlaceId);

        if (!hasText(resolved)) {
            return fallbackPlaceId;
        }

        return resolved;
    }

    private boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    public void submitReview(int stars, String comment) {
        submitOrUpdateReview(null, null, stars, comment, false);
    }

    public void updateReview(@Nullable Review existingReview, int stars, String comment) {
        String expectedPlaceId = existingReview != null ? existingReview.placeId : null;
        submitOrUpdateReview(expectedPlaceId, existingReview, stars, comment, true);
    }

    private void submitOrUpdateReview(@Nullable String expectedPlaceId,
                                      @Nullable Review existingReview,
                                      int stars,
                                      String comment,
                                      boolean isUpdate) {
        if (Boolean.TRUE.equals(_isLoadingReviews.getValue())) {
            return;
        }

        final long submitRequestId = reviewLoadRequestId.get();
        final String placeId = _currentPlaceId.getValue();
        if (placeId == null || placeId.trim().isEmpty()) {
            return;
        }

        if (expectedPlaceId != null
                && !expectedPlaceId.trim().isEmpty()
                && !placeId.equals(expectedPlaceId.trim())) {
            return;
        }

        PlaceIdentityContext identityContext = copyIdentityContext(currentPlaceIdentityContext);
        if (identityContext == null) {
            identityContext = new PlaceIdentityContext();
        }

        if (existingReview != null) {
            if ((identityContext.externalSource == null || identityContext.externalSource.trim().isEmpty())
                    && existingReview.externalSource != null) {
                identityContext.externalSource = existingReview.externalSource;
            }
            if ((identityContext.externalId == null || identityContext.externalId.trim().isEmpty())
                    && existingReview.externalId != null) {
                identityContext.externalId = existingReview.externalId;
            }
            if ((identityContext.placeName == null || identityContext.placeName.trim().isEmpty())
                    && existingReview.placeName != null) {
                identityContext.placeName = existingReview.placeName;
            }
            if ((identityContext.lat == null || identityContext.lat == 0.0d)
                    && existingReview.lat != null) {
                identityContext.lat = existingReview.lat;
            }
            if ((identityContext.lng == null || identityContext.lng == 0.0d)
                    && existingReview.lng != null) {
                identityContext.lng = existingReview.lng;
            }
        }

        final PlaceIdentityContext finalIdentityContext = copyIdentityContext(identityContext);

        reviewExecutor.execute(() -> {
            if (submitRequestId != reviewLoadRequestId.get()) {
                return;
            }
            if (Boolean.TRUE.equals(_isLoadingReviews.getValue())) {
                return;
            }

            String latestPlaceId = _currentPlaceId.getValue();
            if (latestPlaceId == null || !latestPlaceId.equals(placeId)) {
                return;
            }

            if (isUpdate) {
                reviewRepository.updateReview(
                        placeId,
                        stars,
                        comment,
                        finalIdentityContext);
            } else {
                reviewRepository.submitReview(
                        placeId,
                        stars,
                        comment,
                        finalIdentityContext);
            }
        });
    }

    private void updateCurrentReviewMetadata(@Nullable PlaceIdentityContext identityContext) {
        currentPlaceIdentityContext = copyIdentityContext(identityContext);
    }

    @Nullable
    private PlaceIdentityContext copyIdentityContext(@Nullable PlaceIdentityContext source) {
        if (source == null) {
            return null;
        }
        PlaceIdentityContext copy = new PlaceIdentityContext();
        copy.externalSource = source.externalSource;
        copy.externalId = source.externalId;
        copy.lat = source.lat;
        copy.lng = source.lng;
        copy.placeName = source.placeName;
        return copy;
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

        float normalizedBearing = normalizeBearing(bearingDegrees);
        if (isEquivalentFollowingUpdate(
                current.lastKnownLocation,
                location,
                current.lastBearingDegrees,
                normalizedBearing)) {
            return;
        }

        _routeSession.setValue(current.withLocation(cloneLocation(location), normalizedBearing));
    }

    private boolean isEquivalentFollowingUpdate(@Nullable Location previousLocation,
                                                @Nullable Location nextLocation,
                                                float previousBearing,
                                                float nextBearing) {
        if (!isSameLocationWithinMeters(previousLocation, nextLocation, FOLLOWING_LOCATION_MIN_DELTA_METERS)) {
            return false;
        }

        if (!Float.isFinite(previousBearing) || !Float.isFinite(nextBearing)) {
            return Float.isNaN(previousBearing) && Float.isNaN(nextBearing);
        }

        return angularDifferenceDegrees(previousBearing, nextBearing) < FOLLOWING_BEARING_MIN_DELTA_DEGREES;
    }

    private boolean isSameLocationWithinMeters(@Nullable Location first,
                                               @Nullable Location second,
                                               double thresholdMeters) {
        if (first == null || second == null) {
            return first == second;
        }

        double earthRadiusMeters = 6371000.0d;
        double dLat = Math.toRadians(second.latitude - first.latitude);
        double dLng = Math.toRadians(second.longitude - first.longitude);
        double lat1 = Math.toRadians(first.latitude);
        double lat2 = Math.toRadians(second.latitude);

        double sinHalfLat = Math.sin(dLat / 2.0d);
        double sinHalfLng = Math.sin(dLng / 2.0d);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(lat1) * Math.cos(lat2) * sinHalfLng * sinHalfLng;
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0d, 1.0d - a)));
        double distanceMeters = earthRadiusMeters * c;
        return distanceMeters <= thresholdMeters;
    }

    private float angularDifferenceDegrees(float first, float second) {
        float diff = Math.abs(first - second) % 360f;
        return diff > 180f ? 360f - diff : diff;
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

    @Override
    protected void onCleared() {
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        super.onCleared();
    }



    private void dispatchSearchQuery(@Nullable String rawQuery,
            boolean saveToHistory) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            _isSearchingPlaces.postValue(false);
            activeSearchPending.set(false);
            searchTrigger.postValue(SearchTrigger.empty());
            return;
        }

        Location loc = currentSearchUserLocation;
        String locKey = loc == null
                ? "none"
                : String.format(Locale.US, "%.4f,%.4f", loc.latitude, loc.longitude);
        String dedupeKey = query + "|" + locKey + "|" + saveToHistory;
        if (dedupeKey.equals(lastSearchQuery)) {
            _isSearchingPlaces.postValue(false);
            return;
        }
        lastSearchQuery = dedupeKey;

        searchTrigger.postValue(new SearchTrigger(
                query,
                cloneLocation(currentSearchUserLocation),
                saveToHistory));
    }

    private static final class SearchTrigger {
        private final String query;
        @Nullable
        private final Location userLocation;
        private final boolean saveToHistory;

        SearchTrigger(@NonNull String query,
                @Nullable Location userLocation,
                boolean saveToHistory) {
            this.query = query;
            this.userLocation = userLocation;
            this.saveToHistory = saveToHistory;
        }

        static SearchTrigger empty() {
            return new SearchTrigger("", null, true);
        }
    }

    @Nullable
    private Location cloneLocation(@Nullable Location source) {
        if (source == null) {
            return null;
        }
        return new Location(source.latitude, source.longitude);
    }
}
