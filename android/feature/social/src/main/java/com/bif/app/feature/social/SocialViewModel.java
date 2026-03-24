package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.repository.IFriendRepository;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SocialViewModel extends ViewModel {
    private final IFriendRepository friendRepository;
    private final IGroupRepository groupRepository;
    private final LiveData<List<Friend>> friends;
    private final LiveData<List<Group>> groups;
    private final MediatorLiveData<UiState<List<Friend>>> friendUiState = new MediatorLiveData<>();
    private final MediatorLiveData<UiState<List<Group>>> groupUiState = new MediatorLiveData<>();

    @Inject
    public SocialViewModel(IFriendRepository friendRepository, IGroupRepository groupRepository) {
        this.friendRepository = friendRepository;
        this.groupRepository = groupRepository;
        this.friends = friendRepository.getFriends();
        this.groups = groupRepository.getGroups();

        friendUiState.setValue(UiState.loading());
        groupUiState.setValue(UiState.loading());

        friendUiState.addSource(this.friends, this::mapFriendState);
        groupUiState.addSource(this.groups, this::mapGroupState);
    }

    public LiveData<List<Friend>> getFriends() {
        return friends;
    }
    public LiveData<List<Group>> getGroups() { return groups; }

    public LiveData<UiState<List<Friend>>> getFriendUiState() {
        return friendUiState;
    }

    public LiveData<UiState<List<Group>>> getGroupUiState() {
        return groupUiState;
    }

    public void retryFriends() {
        mapFriendState(friends.getValue());
    }

    public void retryGroups() {
        mapGroupState(groups.getValue());
    }

    public void addFriend(String name, String avatarLetter, int avatarColor) {
        try {
            Friend newFriend = new Friend(0, name, avatarLetter, avatarColor, true);
            friendRepository.addFriend(newFriend);
        } catch (RuntimeException exception) {
            friendUiState.setValue(UiState.error("Unable to add friend."));
        }
    }

    public void deleteFriend(Friend friend) {
        try {
            friendRepository.deleteFriend(friend);
        } catch (RuntimeException exception) {
            friendUiState.setValue(UiState.error("Unable to delete friend."));
        }
    }

    public void createGroup(String groupName, List<Friend> selectedMembers) {
        try {
            groupRepository.createGroup(groupName, selectedMembers);
        } catch (RuntimeException exception) {
            groupUiState.setValue(UiState.error("Unable to create group."));
        }
    }

    public void handleGroupAction(Group group) {
        try {
            if (group.isOwner()) {
                groupRepository.disbandGroup(group);
            } else {
                groupRepository.leaveGroup(group);
            }
        } catch (RuntimeException exception) {
            groupUiState.setValue(UiState.error("Unable to update group."));
        }
    }

    private void mapFriendState(List<Friend> friendList) {
        if (friendList == null) {
            friendUiState.setValue(UiState.error("Unable to load friends."));
            return;
        }
        if (friendList.isEmpty()) {
            friendUiState.setValue(UiState.empty("No friends yet."));
            return;
        }
        friendUiState.setValue(UiState.success(friendList));
    }

    private void mapGroupState(List<Group> groupList) {
        if (groupList == null) {
            groupUiState.setValue(UiState.error("Unable to load groups."));
            return;
        }
        if (groupList.isEmpty()) {
            groupUiState.setValue(UiState.empty("No groups yet."));
            return;
        }
        groupUiState.setValue(UiState.success(groupList));
    }
}
