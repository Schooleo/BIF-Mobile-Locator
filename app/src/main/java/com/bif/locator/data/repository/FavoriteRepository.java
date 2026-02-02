package com.bif.locator.data.repository;

import com.bif.locator.domain.model.Favorite;
import com.bif.locator.domain.repository.IFavoriteRepository;
import androidx.lifecycle.LiveData;
import java.util.List;
import javax.inject.Inject;

public class FavoriteRepository implements IFavoriteRepository {

    @Inject
    public FavoriteRepository() {
    }

    @Override
    public LiveData<List<Favorite>> getAllFavorites() {
        return null;
    }

    @Override
    public void addFavorite(Favorite favorite) {

    }

    @Override
    public void deleteFavorite(Favorite favorite) {

    }
}
