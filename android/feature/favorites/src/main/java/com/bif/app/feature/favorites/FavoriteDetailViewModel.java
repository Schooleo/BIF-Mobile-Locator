package com.bif.app.feature.favorites;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Favorite;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IFavoriteRepository;
import com.bif.app.domain.repository.IGroupRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class FavoriteDetailViewModel extends ViewModel {

    private final IFavoriteRepository favoriteRepository;
    private final LiveData<List<Group>> groups;
    private final MutableLiveData<Favorite> currentFavorite = new MutableLiveData<>();

    @Inject
    public FavoriteDetailViewModel(IGroupRepository groupRepository,
                                   IFavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
        this.groups = groupRepository.getGroups();
    }

    public LiveData<List<Group>> getGroups() {
        return groups;
    }

    public LiveData<Favorite> getCurrentFavorite() {
        return currentFavorite;
    }

    public void initializeFavorite(@NonNull Favorite favorite) {
        Favorite existing = currentFavorite.getValue();
        if (existing != null && safeEquals(existing.id, favorite.id)) {
            return;
        }
        currentFavorite.setValue(copyFavorite(favorite));
    }

    public void updateNotes(String notes) {
        Favorite favorite = currentFavorite.getValue();
        if (favorite == null) {
            return;
        }

        Favorite updatedFavorite = copyFavorite(favorite);
        updatedFavorite.notes = notes != null ? notes.trim() : "";
        currentFavorite.setValue(updatedFavorite);
        favoriteRepository.updateFavorite(updatedFavorite);
    }

    private Favorite copyFavorite(@NonNull Favorite source) {
        Favorite copy = new Favorite();
        copy.id = source.id;
        copy.name = source.name;
        copy.latitude = source.latitude;
        copy.longitude = source.longitude;
        copy.address = source.address;
        copy.description = source.description;
        copy.notes = source.notes;
        copy.rating = source.rating;
        copy.serverVersion = source.serverVersion;
        copy.deleted = source.deleted;
        copy.userId = source.userId;
        return copy;
    }

    private boolean safeEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }
}
