package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Favorite;

import java.util.List;

public interface IFavoriteRepository {
    String ERROR_REFRESH_FAILED = "favorite_refresh_failed";

    interface SyncCallback {
        void onSuccess();
        void onOffline();
        void onError(String message);
    }

    LiveData<List<Favorite>> getAllFavorites();
    LiveData<List<Favorite>> searchFavorites(String query);
    void addFavorite(Favorite favorite);
    void updateFavorite(Favorite favorite);
    void updateAllFavorites(List<Favorite> favorites);
    void deleteFavorite(Favorite favorite);
    void refreshFavorites(SyncCallback callback);
}
