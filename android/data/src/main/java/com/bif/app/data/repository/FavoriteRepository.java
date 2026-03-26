package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.favorite.FavoriteResponseDto;
import com.bif.app.data.mapper.FavoriteMapper;
import com.bif.app.data.source.local.FavoriteDao;
import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.repository.IFavoriteRepository;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import retrofit2.Call;
import retrofit2.Response;

public class FavoriteRepository implements IFavoriteRepository {

    private final FavoriteDao favoriteDao;
    private final RestApiService restApiService;
    private final ExecutorService executorService;

    @Inject
    public FavoriteRepository(FavoriteDao favoriteDao, RestApiService restApiService) {
        this.favoriteDao = favoriteDao;
        this.restApiService = restApiService;
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
        executorService.execute(() -> {
            favoriteDao.insert(FavoriteMapper.toEntity(favorite));
            try {
                Call<FavoriteResponseDto> call = restApiService.upsertMyFavorite(FavoriteMapper.toRequestDto(favorite));
                if (call != null) {
                    call.execute();
                }
            } catch (IOException ignored) {
            }
        });
    }

    @Override
    public void updateFavorite(Favorite favorite) {
        executorService.execute(() -> {
            favoriteDao.update(FavoriteMapper.toEntity(favorite));
            try {
                Call<FavoriteResponseDto> call = restApiService.upsertMyFavorite(FavoriteMapper.toRequestDto(favorite));
                if (call != null) {
                    call.execute();
                }
            } catch (IOException ignored) {
            }
        });
    }

    @Override
    public void updateAllFavorites(List<Favorite> favorites) {
        executorService.execute(() -> {
            favoriteDao.updateAll(FavoriteMapper.toEntityList(favorites));
            if (favorites == null) {
                return;
            }
            for (Favorite favorite : favorites) {
                try {
                    Call<FavoriteResponseDto> call = restApiService.upsertMyFavorite(FavoriteMapper.toRequestDto(favorite));
                    if (call != null) {
                        call.execute();
                    }
                } catch (IOException ignored) {
                }
            }
        });
    }

    @Override
    public void deleteFavorite(Favorite favorite) {
        executorService.execute(() -> {
            favoriteDao.delete(FavoriteMapper.toEntity(favorite));
            if (favorite == null || favorite.id == null || favorite.id.trim().isEmpty()) {
                return;
            }
            try {
                Call<Void> call = restApiService.deleteMyFavorite(favorite.id);
                if (call != null) {
                    call.execute();
                }
            } catch (IOException ignored) {
            }
        });
    }

    @Override
    public void refreshFavorites(SyncCallback callback) {
        executorService.execute(() -> {
            try {
                Call<List<FavoriteResponseDto>> call = restApiService.getMyFavorites();
                if (call == null) {
                    if (callback != null) {
                        callback.onError("Unable to sync favorites");
                    }
                    return;
                }

                Response<List<FavoriteResponseDto>> response = call.execute();
                if (!response.isSuccessful() || response.body() == null) {
                    if (callback != null) {
                        callback.onError("Unable to sync favorites");
                    }
                    return;
                }

                List<Favorite> favorites = FavoriteMapper.toDomainListFromDto(response.body());
                favoriteDao.replaceAll(FavoriteMapper.toEntityList(favorites));
                if (callback != null) {
                    callback.onSuccess();
                }
            } catch (IOException e) {
                if (callback != null) {
                    callback.onError("Offline mode: showing local favorites");
                }
            }
        });
    }
}
