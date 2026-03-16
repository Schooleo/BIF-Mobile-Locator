package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
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

    @Inject
    public SocialViewModel(IFriendRepository friendRepository, IGroupRepository groupRepository) {
        this.friendRepository = friendRepository;
        this.groupRepository = groupRepository;
        this.friends = friendRepository.getFriends();
        this.groups = groupRepository.getGroups();
    }

    public LiveData<List<Friend>> getFriends() {
        return friends;
    }
    public LiveData<List<Group>> getGroups() { return groups; }

    public void addFriend(String name, String avatarLetter, int avatarColor) {
        Friend newFriend = new Friend(0, name, avatarLetter, avatarColor, true);
        friendRepository.addFriend(newFriend);
    }

    public void deleteFriend(Friend friend) {
        friendRepository.deleteFriend(friend);
    }

    public void createGroup(String groupName, List<Friend> selectedMembers) {
        groupRepository.createGroup(groupName, selectedMembers);
    }

    public void handleGroupAction(Group group) {
        if (group.isOwner()) {
            groupRepository.disbandGroup(group);
        } else {
            groupRepository.leaveGroup(group);
        }
    }
}
