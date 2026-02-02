package com.bif.locator.domain.repository;

import com.bif.locator.domain.model.Favorite;
import java.util.List;
import androidx.lifecycle.LiveData;

public interface IFavoriteRepository {
    LiveData<List<Favorite>> getAllFavorites();
    void addFavorite(Favorite favorite);
    void deleteFavorite(Favorite favorite);
}
