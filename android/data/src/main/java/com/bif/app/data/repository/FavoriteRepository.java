package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

public class FavoriteRepository implements IFavoriteRepository {

    private final FavoriteDao favoriteDao;
    private final ExecutorService executorService;

    @Inject
    public FavoriteRepository(FavoriteDao favoriteDao) {
        this.favoriteDao = favoriteDao;
        this.executorService = Executors.newFixedThreadPool(4);
    }

    @Override
    public LiveData<List<Favorite>> searchFavorites(String query) {
        return Transformations.map(favoriteDao.searchFavorites(query), FavoriteMapper::toDomainList);
    }

    @Override
    public LiveData<List<Favorite>> getAllFavorites() {
        return Transformations.map(favoriteDao.getAll(), FavoriteMapper::toDomainList);
    }

    @Override
    public void addFavorite(Favorite favorite) {
        executorService.execute(() -> favoriteDao.insert(FavoriteMapper.toEntity(favorite)));
    }

    @Override
    public void updateFavorite(Favorite favorite) {
        executorService.execute(() -> favoriteDao.update(FavoriteMapper.toEntity(favorite)));
    }

    @Override
    public void updateAllFavorites(List<Favorite> favorites) {
        executorService.execute(() -> favoriteDao.updateAll(FavoriteMapper.toEntityList(favorites)));
    }

    @Override
    public void deleteFavorite(Favorite favorite) {
        executorService.execute(() -> favoriteDao.delete(FavoriteMapper.toEntity(favorite)));
    }
}
