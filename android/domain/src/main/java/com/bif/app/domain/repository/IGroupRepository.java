package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.model.Friend;
import java.util.List;

public interface IGroupRepository {
    LiveData<List<Group>> getGroups();
    void createGroup(String name, List<Friend> selectedFriends);
    void leaveGroup(Group group);
    void disbandGroup(Group group);
}