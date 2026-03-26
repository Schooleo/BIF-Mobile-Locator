package com.bif.app.feature.favorites;

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

    private final IFavoriteRepository favoriteRepository;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MediatorLiveData<List<Favorite>> _favorites = new MediatorLiveData<>();
    public final LiveData<List<Favorite>> favorites = _favorites;
    private final MutableLiveData<Boolean> _isSyncing = new MutableLiveData<>(false);
    public final LiveData<Boolean> isSyncing = _isSyncing;
    private final MutableLiveData<String> _syncMessage = new MutableLiveData<>();
    public final LiveData<String> syncMessage = _syncMessage;

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
        _isSyncing.setValue(true);
        favoriteRepository.refreshFavorites(new IFavoriteRepository.SyncCallback() {
            @Override
            public void onSuccess() {
                _isSyncing.postValue(false);
                _syncMessage.postValue("");
            }

            @Override
            public void onError(String message) {
                _isSyncing.postValue(false);
                _syncMessage.postValue(message);
            }
        });
    }

}
