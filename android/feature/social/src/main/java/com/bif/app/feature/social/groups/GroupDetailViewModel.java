package com.bif.app.feature.social.groups;

import com.bif.app.feature.social.R;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.bif.app.domain.repository.IGroupRepository;

import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class GroupDetailViewModel extends ViewModel {
    private final IGroupRepository groupRepository;
    private final LiveData<List<Friend>> friends;
    private final MutableLiveData<String> groupIdLiveData = new MutableLiveData<>();
    private final LiveData<Group> group;

    @Inject
    public GroupDetailViewModel(IGroupRepository groupRepository,
                                IFriendshipRepository friendshipRepository) {
        this.groupRepository = groupRepository;
        this.friends = friendshipRepository.getFriends();
        this.group = Transformations.switchMap(groupIdLiveData, groupRepository::getGroupByServerId);
    }

    public void loadGroup(String groupId) {
        groupIdLiveData.setValue(groupId);
    }

    public void loadGroup(int groupId) {
        loadGroup(String.valueOf(groupId));
    }

    public LiveData<Group> getGroup() {
        return group;
    }

    public LiveData<List<Friend>> getFriends() {
        return friends;
    }

    public void updateGroupName(String newName) {
        Group currentGroup = group.getValue();
        if (currentGroup == null) return;

        Group updatedGroup = new Group(
                currentGroup.getId(),
                currentGroup.getServerId(),
                newName,
                newName.substring(0, 1).toUpperCase(),
                currentGroup.getAvatarColor(),
                currentGroup.getMembers(),
                currentGroup.isOwner(),
                currentGroup.getMemberRoles() != null ? new HashMap<>(currentGroup.getMemberRoles()) : null
        );
        groupRepository.updateGroup(updatedGroup);
    }

    public void removeMember(Friend member) {
        Group currentGroup = group.getValue();
        if (currentGroup == null) return;
        groupRepository.removeMemberByServerId(currentGroup.getServerId(), member.getId());
    }

    public void addMembers(List<Friend> selectedFriends) {
        Group currentGroup = group.getValue();
        if (currentGroup == null || selectedFriends == null || selectedFriends.isEmpty()) {
            return;
        }

        for (Friend friend : selectedFriends) {
            if (friend == null) {
                continue;
            }
            groupRepository.addMemberByServerId(currentGroup.getServerId(), friend.getId());
        }
    }

    public void updateMemberRole(Friend member, String role) {
        Group currentGroup = group.getValue();
        if (currentGroup == null || member == null) {
            return;
        }
        groupRepository.updateMemberRoleByServerId(currentGroup.getServerId(), member.getId(), role);
    }

    public void disbandGroup() {
        Group currentGroup = group.getValue();
        if (currentGroup == null) return;
        groupRepository.disbandGroup(currentGroup);
    }

    public void refreshGroup() {
        groupRepository.refreshGroups();
    }
}
