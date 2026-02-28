package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Favorite;

import java.util.List;

public interface IFavoriteRepository {
    LiveData<List<Favorite>> getAllFavorites();
    void addFavorite(Favorite favorite);
    void deleteFavorite(Favorite favorite);
}
