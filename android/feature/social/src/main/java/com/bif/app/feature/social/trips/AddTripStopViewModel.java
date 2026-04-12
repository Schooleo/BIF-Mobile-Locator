package com.bif.app.feature.social;

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
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AddTripStopViewModel extends ViewModel {

    private static final String OFFLINE_FAILURE_CODE = "OFFLINE";

    private final IPlaceRepository placeRepository;
    private final ITripRepository tripRepository;
    private final NetworkMonitor networkMonitor;

    private final MutableLiveData<Boolean> aiModeEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> aiToggleEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<String> searchHint = new MutableLiveData<>("Search places...");
    private final MutableLiveData<SearchState> searchState = new MutableLiveData<>(new SearchState.Idle());
    private final AtomicInteger currentSearchToken = new AtomicInteger(0);

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
                                NetworkMonitor networkMonitor) {
        this.placeRepository = placeRepository;
        this.tripRepository = tripRepository;
        this.networkMonitor = networkMonitor;

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

    public void setTripId(@NonNull String tripId) {
        this.currentTripId = tripId;
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
                0
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

        LiveData<AiPlaceSuggestionResult> source = placeRepository.suggestPlacesFromQuery(query);
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
