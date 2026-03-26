package com.bif.app.feature.favorites;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class FavoriteDetailViewModel extends ViewModel {

    private final LiveData<List<Group>> groups;

    @Inject
    public FavoriteDetailViewModel(IGroupRepository groupRepository) {
        this.groups = groupRepository.getGroups();
    }

    public LiveData<List<Group>> getGroups() {
        return groups;
    }
}
