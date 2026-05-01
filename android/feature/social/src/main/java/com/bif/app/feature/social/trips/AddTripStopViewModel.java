package com.bif.app.feature.social.trips;

import com.bif.app.feature.social.R;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.domain.model.AiPlaceSuggestion;
import com.bif.app.domain.model.AiPlaceSuggestionResult;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.IReviewRepository;
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AddTripStopViewModel extends ViewModel {

    private static final String OFFLINE_FAILURE_CODE = "OFFLINE";

    private final IPlaceRepository placeRepository;
    private final ITripRepository tripRepository;
    private final IReviewRepository reviewRepository;
    private final NetworkMonitor networkMonitor;
    private final Executor executor;

    private final MutableLiveData<Boolean> aiModeEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> aiToggleEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<String> searchHint = new MutableLiveData<>("Search places...");
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<>(new SearchState.Idle());
    private final MutableLiveData<Place> selectedPlaceDetail = new MutableLiveData<>();

    private final AtomicInteger currentSearchToken = new AtomicInteger(0);
    private final AtomicLong currentResolveRequestId = new AtomicLong(0L);
    private volatile Double aiBiasLatitude;
    private volatile Double aiBiasLongitude;
    private volatile String aiCityBias;

    private String currentTripId = "";

    private final Observer<Boolean> networkObserver = isOnline -> {
        boolean online = Boolean.TRUE.equals(isOnline);
        aiToggleEnabled.postValue(online);
        if (!online) {
            aiModeEnabled.postValue(false);
            searchHint.postValue("Search places...");
        }
    };

    @Inject
    public AddTripStopViewModel(IPlaceRepository placeRepository,
                                ITripRepository tripRepository,
                                IReviewRepository reviewRepository,
                                NetworkMonitor networkMonitor,
                                Executor executor) {
        this.placeRepository = placeRepository;
        this.tripRepository = tripRepository;
        this.reviewRepository = reviewRepository;
        this.networkMonitor = networkMonitor;
        this.executor = executor;

        aiToggleEnabled.setValue(networkMonitor.isOnline());
        networkMonitor.observeConnectivity().observeForever(networkObserver);
    }

    public LiveData<Boolean> getAiModeEnabled() {
        return aiModeEnabled;
    }

    public LiveData<Boolean> getAiToggleEnabled() {
        return aiToggleEnabled;
    }

    public LiveData<String> getSearchHint() {
        return searchHint;
    }

    public LiveData<SearchState> getSearchState() {
        return searchState;
    }

    public LiveData<Place> getSelectedPlaceDetail() {
        return selectedPlaceDetail;
    }

    public void setTripId(@NonNull String tripId) {
        this.currentTripId = tripId;
    }

    public void setAiSearchBias(Double latitude, Double longitude, String cityBias) {
        this.aiBiasLatitude = latitude;
        this.aiBiasLongitude = longitude;
        this.aiCityBias = cityBias;
    }

    public void toggleAiMode() {
        if (!networkMonitor.isOnline()) {
            aiModeEnabled.setValue(false);
            aiToggleEnabled.setValue(false);
            searchHint.setValue("Search places...");
            return;
        }

        boolean enabled = !Boolean.TRUE.equals(aiModeEnabled.getValue());
        aiModeEnabled.setValue(enabled);
        searchHint.setValue(enabled ? "Describe your vibe..." : "Search places...");
    }

    public void search(@NonNull String rawQuery) {
        int requestToken = currentSearchToken.incrementAndGet();
        String query = rawQuery.trim();
        if (query.isEmpty()) {
            searchState.setValue(new SearchState.Idle());
            return;
        }

        if (Boolean.TRUE.equals(aiModeEnabled.getValue())) {
            runAiSearch(query);
            return;
        }

        searchState.setValue(new SearchState.Loading(false));
        LiveData<List<Place>> source = placeRepository.searchPlaces(query, null);
        observeOnce(source, places -> {
            if (requestToken != currentSearchToken.get()) {
                return;
            }

            List<StopSearchResultItem> mapped = new ArrayList<>();
            if (places != null) {
                for (Place place : places) {
                    mapped.add(new StopSearchResultItem(place, 0));
                }
            }
            if (mapped.isEmpty()) {
                searchState.setValue(new SearchState.Empty(
                        "No places found. Try a different search."));
            } else {
                searchState.setValue(new SearchState.Success(mapped));
            }
        });
    }

    public void resolveSelectedPlace(@NonNull Place place) {
        selectedPlaceDetail.setValue(place);
        if (place.location == null) {
            return;
        }

        final long requestId = currentResolveRequestId.incrementAndGet();
        executor.execute(() -> {
            String serverId = reviewRepository.resolveInternalPlaceId(
                    place.placeSource,
                    place.id,
                    place.location.latitude,
                    place.location.longitude,
                    place.name);

            if (requestId != currentResolveRequestId.get()) {
                return;
            }

            if (serverId != null && !serverId.trim().isEmpty()) {
                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                mainHandler.post(() -> {
                    LiveData<Place> source = placeRepository.getPlaceById(serverId);
                    source.observeForever(new Observer<Place>() {
                        @Override
                        public void onChanged(Place detailedPlace) {
                            source.removeObserver(this);
                            if (requestId == currentResolveRequestId.get() && detailedPlace != null) {
                                Place current = selectedPlaceDetail.getValue();
                                if (current != null) {
                                    double mergedRating = detailedPlace.rating > 0 ? detailedPlace.rating : current.rating;
                                    Location mergedLocation = (detailedPlace.location != null && (Math.abs(detailedPlace.location.latitude) > 0.000001 || Math.abs(detailedPlace.location.longitude) > 0.000001))
                                            ? detailedPlace.location
                                            : current.location;

                                    Place merged = new Place(
                                            detailedPlace.id,
                                            detailedPlace.name != null && !detailedPlace.name.trim().isEmpty() ? detailedPlace.name : current.name,
                                            detailedPlace.address != null && !detailedPlace.address.trim().isEmpty() ? detailedPlace.address : current.address,
                                            mergedRating,
                                            mergedLocation,
                                            detailedPlace.placeSource != null ? detailedPlace.placeSource : current.placeSource
                                    );
                                    selectedPlaceDetail.setValue(merged);
                                } else {
                                    selectedPlaceDetail.setValue(detailedPlace);
                                }
                            }
                        }
                    });
                });
            }
        });
    }

    public boolean addStopToTrip(@NonNull StopSearchResultItem item, long scheduledAtMillis) {
        if (currentTripId == null || currentTripId.trim().isEmpty() || item.place == null) {
            return false;
        }

        Place place = item.place;
        Location location = place.location;
        if (location == null) {
            return false;
        }
        TripStop stop = new TripStop(
                UUID.randomUUID().toString(),
                place.name,
                place.address,
                "",
                "",
                "",
                location.latitude,
                location.longitude,
                scheduledAtMillis,
                scheduledAtMillis,
                0,
                "",
                "",
                "",
                0,
                place.rating
        );
        tripRepository.addStopToTrip(currentTripId, stop);
        return true;
    }

    @NonNull
    public String getCurrentTripId() {
        return currentTripId == null ? "" : currentTripId;
    }

    private void runAiSearch(@NonNull String query) {
        int requestToken = currentSearchToken.incrementAndGet();

        if (!networkMonitor.isOnline()) {
            aiModeEnabled.setValue(false);
            aiToggleEnabled.setValue(false);
            searchHint.setValue("Search places...");
            searchState.setValue(new SearchState.Empty(
                    "We couldn't find any places matching your vibe."));
            return;
        }

        searchState.setValue(new SearchState.Loading(true));

        LiveData<AiPlaceSuggestionResult> source = placeRepository.suggestPlacesFromQuery(
                query,
                aiBiasLatitude,
                aiBiasLongitude,
                aiCityBias);
        observeOnce(source, result -> {
            if (requestToken != currentSearchToken.get()) {
                return;
            }

            if (result == null) {
                searchState.setValue(new SearchState.Empty(
                        "We couldn't find any places matching your vibe."));
                return;
            }

            String failureCode = result.getFailureCode();
            if (failureCode != null && !failureCode.trim().isEmpty()) {
                if (OFFLINE_FAILURE_CODE.equalsIgnoreCase(failureCode)) {
                    aiModeEnabled.setValue(false);
                    aiToggleEnabled.setValue(false);
                    searchHint.setValue("Search places...");
                }
                searchState.setValue(new SearchState.Empty(
                        "We couldn't find any places matching your vibe."));
                return;
            }

            List<StopSearchResultItem> mapped = new ArrayList<>();
            List<AiPlaceSuggestion> suggestions = result.getPlaces();
            if (suggestions != null) {
                for (AiPlaceSuggestion suggestion : suggestions) {
                    if (suggestion == null || suggestion.getPlace() == null) {
                        continue;
                    }
                    mapped.add(new StopSearchResultItem(
                            suggestion.getPlace(),
                            Math.max(0, suggestion.getAddedToTripCount())));
                }
            }

            if (mapped.isEmpty()) {
                searchState.setValue(new SearchState.Empty(
                        "We couldn't find any places matching your vibe."));
            } else {
                searchState.setValue(new SearchState.Success(mapped));
            }
        });
    }

    private <T> void observeOnce(@NonNull LiveData<T> source, @NonNull Observer<T> observer) {
        source.observeForever(new Observer<T>() {
            @Override
            public void onChanged(T value) {
                source.removeObserver(this);
                observer.onChanged(value);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        networkMonitor.observeConnectivity().removeObserver(networkObserver);
    }

    public static class StopSearchResultItem {
        public final Place place;
        public final int addedToTripCount;

        public StopSearchResultItem(Place place, int addedToTripCount) {
            this.place = place;
            this.addedToTripCount = addedToTripCount;
        }
    }

    public abstract static class SearchState {

        public static final class Idle extends SearchState {
        }

        public static final class Loading extends SearchState {
            public final boolean aiMode;

            public Loading(boolean aiMode) {
                this.aiMode = aiMode;
            }
        }

        public static final class Success extends SearchState {
            public final List<StopSearchResultItem> items;

            public Success(List<StopSearchResultItem> items) {
                this.items = items == null ? Collections.emptyList() : new ArrayList<>(items);
            }
        }

        public static final class Empty extends SearchState {
            public final String message;

            public Empty(String message) {
                this.message = message;
            }
        }
    }
}
