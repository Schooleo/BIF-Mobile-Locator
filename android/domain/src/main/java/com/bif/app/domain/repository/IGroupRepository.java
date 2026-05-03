package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Friend;
import java.util.List;

public interface IGroupRepository {
    LiveData<List<Group>> getGroups();
    LiveData<Group> getGroupById(int groupId);
    default LiveData<Group> getGroupByServerId(String groupId) {
        return getGroupById(Integer.parseInt(groupId));
    }
    void createGroup(String name, List<Friend> selectedFriends);
    void updateGroup(Group group);
    void addMember(int groupId, int friendId);
    default void addMemberByServerId(String groupId, int friendId) {
        try {
            addMember(Integer.parseInt(groupId), friendId);
        } catch (NumberFormatException ignored) {
        }
    }
    void removeMember(int groupId, int friendId);
    default void removeMemberByServerId(String groupId, int friendId) {
        removeMember(Integer.parseInt(groupId), friendId);
    }
    void updateMemberRole(int groupId, int friendId, String role);
    default void updateMemberRoleByServerId(String groupId, int friendId, String role) {
        try {
            updateMemberRole(Integer.parseInt(groupId), friendId, role);
        } catch (NumberFormatException ignored) {
        }
    }
    void leaveGroup(Group group);
    void disbandGroup(Group group);
    void refreshGroups();
}