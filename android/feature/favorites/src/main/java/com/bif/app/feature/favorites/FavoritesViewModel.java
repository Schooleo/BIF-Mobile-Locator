package com.bif.app.feature.favorites;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;

import java.util.List;

import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class FavoritesViewModel extends ViewModel {

    private static final long AUTO_REFRESH_STALE_MS = 30_000L;
    private static final String TAG = "FavoritesViewModel";

    private final IFavoriteRepository favoriteRepository;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<List<Favorite>> _favorites = new MediatorLiveData<>();
    public final LiveData<List<Favorite>> favorites = _favorites;
    private final MutableLiveData<Boolean> _isSyncing = new MutableLiveData<>(false);
    public final LiveData<Boolean> isSyncing = _isSyncing;
    private final MutableLiveData<String> _syncMessage = new MutableLiveData<>();
    public final LiveData<String> syncMessage = _syncMessage;
    private volatile boolean refreshInProgress;
    private volatile long lastSuccessfulRefreshAtMs;

    @Inject
    public FavoritesViewModel(IFavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;

        // Set up the mediator to switch between all favorites and search results
        LiveData<List<Favorite>> allFavoritesLiveData = favoriteRepository.getAllFavorites();
        LiveData<List<Favorite>> searchResultsLiveData = Transformations.switchMap(
                searchQuery,
                query -> {
                    if (query == null || query.trim().isEmpty()) {
                        return favoriteRepository.getAllFavorites();
                    } else {
                        return favoriteRepository.searchFavorites(query);
                    }
                }
        );

        _favorites.addSource(searchResultsLiveData, _favorites::setValue);
        refreshFavorites();
    }

    public void removeFavoriteItem(Favorite favorite) {
        favoriteRepository.deleteFavorite(favorite);
    }

    public void filterFavorites(String query) {
        searchQuery.setValue(query);
    }

    public void refreshFavorites() {
        if (refreshInProgress) {
            return;
        }

        refreshInProgress = true;
        _isSyncing.setValue(true);
        try {
            favoriteRepository.refreshFavorites(new IFavoriteRepository.SyncCallback() {
                @Override
                public void onSuccess() {
                    refreshInProgress = false;
                    lastSuccessfulRefreshAtMs = System.currentTimeMillis();
                    _isSyncing.postValue(false);
                    _syncMessage.postValue("");
                }

                @Override
                public void onError(String message) {
                    refreshInProgress = false;
                    _isSyncing.postValue(false);
                    _syncMessage.postValue(message);
                }
            });
        } catch (RuntimeException ex) {
            refreshInProgress = false;
            String message = ex.getMessage() != null ? ex.getMessage() : "Failed to refresh favorites";
            Log.e(TAG, "Synchronous favorite refresh failure", ex);
            _isSyncing.postValue(false);
            _syncMessage.postValue(message);
        }
    }

    public void refreshFavoritesIfStale() {
        if (refreshInProgress || Boolean.TRUE.equals(_isSyncing.getValue())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (lastSuccessfulRefreshAtMs > 0L
                && (now - lastSuccessfulRefreshAtMs) < AUTO_REFRESH_STALE_MS) {
            return;
        }

        refreshFavorites();
    }

}
