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
    }

    public void removeFavoriteItem(Favorite favorite) {
        favoriteRepository.deleteFavorite(favorite);
    }

    public void filterFavorites(String query) {
        searchQuery.setValue(query);
    }

}
