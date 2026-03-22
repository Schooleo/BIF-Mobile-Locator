package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
public class GroupRepositoryMock implements IGroupRepository {

    private final ExecutorService executorService;
    private final MutableLiveData<List<Group>> groupsLiveData;

    @Inject
    public GroupRepositoryMock() {
        this.executorService = Executors.newSingleThreadExecutor();
        this.groupsLiveData = new MutableLiveData<>(buildInitialGroups());
    }

    @Override
    public LiveData<List<Group>> getGroups() {
        return groupsLiveData;
    }

    @Override
    public LiveData<Group> getGroupById(int groupId) {
        MutableLiveData<Group> groupLiveData = new MutableLiveData<>();
        groupLiveData.setValue(findById(groupId));
        return groupLiveData;
    }

    @Override
    public LiveData<Group> getGroupByServerId(String groupId) {
        MutableLiveData<Group> groupLiveData = new MutableLiveData<>();
        groupLiveData.setValue(findByServerId(groupId));
        return groupLiveData;
    }

    @Override
    public void createGroup(String name, List<Friend> selectedFriends) {
        executorService.execute(() -> {
            List<Group> current = safeCopy(groupsLiveData.getValue());
            int newId = current.isEmpty() ? 1 : current.get(current.size() - 1).getId() + 1;
            String avatarLetter = name == null || name.isBlank()
                    ? "G"
                    : String.valueOf(name.trim().charAt(0)).toUpperCase();
            List<Friend> members = selectedFriends != null ? new ArrayList<>(selectedFriends) : new ArrayList<>();

            Group newGroup = new Group(
                    newId,
                    String.valueOf(newId),
                    name,
                    avatarLetter,
                    0xFF03DAC5,
                    members,
                    true
            );

            current.add(newGroup);
            groupsLiveData.postValue(current);
        });
    }

    @Override
    public void updateGroup(Group group) {
        if (group == null) {
            return;
        }
        executorService.execute(() -> {
            List<Group> current = safeCopy(groupsLiveData.getValue());
            for (int i = 0; i < current.size(); i++) {
                Group item = current.get(i);
                if (item.getId() == group.getId()) {
                    current.set(i, group);
                    groupsLiveData.postValue(current);
                    return;
                }
            }
        });
    }

    @Override
    public void removeMember(int groupId, int friendId) {
        executorService.execute(() -> {
            List<Group> current = safeCopy(groupsLiveData.getValue());
            for (int i = 0; i < current.size(); i++) {
                Group group = current.get(i);
                if (group.getId() != groupId || group.getMembers() == null) {
                    continue;
                }

                List<Friend> members = new ArrayList<>(group.getMembers());
                members.removeIf(member -> member.getId() == friendId);

                Group updated = new Group(
                        group.getId(),
                        group.getServerId(),
                        group.getName(),
                        group.getAvatarLetter(),
                        group.getAvatarColor(),
                        members,
                        group.isOwner()
                );
                current.set(i, updated);
                groupsLiveData.postValue(current);
                return;
            }
        });
    }

    @Override
    public void removeMemberByServerId(String groupId, int friendId) {
        Group group = findByServerId(groupId);
        if (group == null) {
            return;
        }
        removeMember(group.getId(), friendId);
    }

    @Override
    public void leaveGroup(Group group) {
        disbandGroup(group);
    }

    @Override
    public void disbandGroup(Group group) {
        if (group == null) {
            return;
        }
        executorService.execute(() -> {
            List<Group> current = safeCopy(groupsLiveData.getValue());
            current.removeIf(item -> item.getId() == group.getId());
            groupsLiveData.postValue(current);
        });
    }

    private Group findById(int groupId) {
        List<Group> groups = groupsLiveData.getValue();
        if (groups == null) {
            return null;
        }
        for (Group group : groups) {
            if (group.getId() == groupId) {
                return group;
            }
        }
        return null;
    }

    private Group findByServerId(String groupId) {
        List<Group> groups = groupsLiveData.getValue();
        if (groups == null || groupId == null) {
            return null;
        }
        for (Group group : groups) {
            if (groupId.equals(group.getServerId())) {
                return group;
            }
        }
        return null;
    }

    private List<Group> safeCopy(List<Group> groups) {
        return groups != null ? new ArrayList<>(groups) : new ArrayList<>();
    }

    private List<Group> buildInitialGroups() {
        List<Group> groups = new ArrayList<>();

        List<Friend> members = new ArrayList<>();
        members.add(new Friend(1, "An", "A", 0xFF1565C0, true));
        members.add(new Friend(2, "Binh", "B", 0xFFE65100, false));

        groups.add(new Group(
                1,
                "1",
                "Weekend Explorers",
                "W",
                0xFF1565C0,
                members,
                true
        ));

        return groups;
    }
}
