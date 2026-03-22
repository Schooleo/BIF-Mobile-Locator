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
    void removeMember(int groupId, int friendId);
    default void removeMemberByServerId(String groupId, int friendId) {
        removeMember(Integer.parseInt(groupId), friendId);
    }
    void leaveGroup(Group group);
    void disbandGroup(Group group);
}