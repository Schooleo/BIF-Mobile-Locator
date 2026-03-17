package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class GroupDetailViewModel extends ViewModel {
    private final IGroupRepository groupRepository;
    private final MutableLiveData<Integer> groupIdLiveData = new MutableLiveData<>();
    private final LiveData<Group> group;

    @Inject
    public GroupDetailViewModel(IGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
        this.group = Transformations.switchMap(groupIdLiveData, groupRepository::getGroupById);
    }

    public void loadGroup(int groupId) {
        groupIdLiveData.setValue(groupId);
    }

    public LiveData<Group> getGroup() {
        return group;
    }

    public void updateGroupName(String newName) {
        Group currentGroup = group.getValue();
        if (currentGroup == null) return;

        Group updatedGroup = new Group(
                currentGroup.getId(),
                newName,
                newName.substring(0, 1).toUpperCase(),
                currentGroup.getAvatarColor(),
                currentGroup.getMembers(),
                currentGroup.isOwner()
        );
        groupRepository.updateGroup(updatedGroup);
    }

    public void removeMember(Friend member) {
        Group currentGroup = group.getValue();
        if (currentGroup == null) return;
        groupRepository.removeMember(currentGroup.getId(), member.getId());
    }

    public void disbandGroup() {
        Group currentGroup = group.getValue();
        if (currentGroup == null) return;
        groupRepository.disbandGroup(currentGroup);
    }
}
