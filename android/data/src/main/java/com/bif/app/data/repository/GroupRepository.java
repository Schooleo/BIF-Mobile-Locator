package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.data.mapper.GroupMapper;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GroupRepository implements IGroupRepository {

    private final GroupDao groupDao;
    private final GroupMapper groupMapper;
    private final ExecutorService executorService;

    @Inject
    public GroupRepository(GroupDao groupDao, GroupMapper groupMapper) {
        this.groupDao = groupDao;
        this.groupMapper = groupMapper;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public LiveData<List<Group>> getGroups() {
        return Transformations.map(groupDao.getAllGroupsWithFriends(), groupMapper::mapToDomainList);
    }

    @Override
    public LiveData<Group> getGroupById(int groupId) {
        return Transformations.map(groupDao.getGroupWithFriendsById(groupId), groupMapper::mapToDomain);
    }

    @Override
    public void updateGroup(Group group) {
        executorService.execute(() -> groupDao.updateGroup(groupMapper.mapToEntity(group)));
    }

    @Override
    public void removeMember(int groupId, int friendId) {
        executorService.execute(() -> groupDao.deleteGroupFriendCrossRef(groupId, friendId));
    }

    @Override
    public void createGroup(String name, List<Friend> selectedFriends) {
        executorService.execute(() -> {
            GroupEntity newGroup = new GroupEntity(
                    0,
                    name,
                    name.substring(0, 1).toUpperCase(),
                    0xFF03DAC5,
                    true
            );

            long newGroupId = groupDao.insertGroup(newGroup);

            if (selectedFriends != null && !selectedFriends.isEmpty()) {
                List<GroupFriendCrossRef> crossRefs = new ArrayList<>();
                for (Friend friend : selectedFriends) {
                    crossRefs.add(new GroupFriendCrossRef((int) newGroupId, friend.getId()));
                }
                groupDao.insertGroupFriendCrossRefs(crossRefs);
            }
        });
    }

    @Override
    public void leaveGroup(Group group) {
        executorService.execute(() -> groupDao.deleteGroupById(group.getId()));
    }

    @Override
    public void disbandGroup(Group group) {
        executorService.execute(() -> groupDao.deleteGroupById(group.getId()));
    }
}