package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.AddMemberRequestDto;
import com.bif.app.core.network.dto.CreateGroupRequestDto;
import com.bif.app.core.network.dto.GroupApiModel;
import com.bif.app.core.network.dto.UpdateGroupRequestDto;
import com.bif.app.core.network.dto.UpdateMemberRoleRequestDto;
import com.bif.app.core.network.dto.UserApiModel;
import com.bif.app.data.mapper.GroupMapper;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Response;

@Singleton
public class GroupRepository implements IGroupRepository {

    private final RestApiService restApiService;

    private final GroupDao groupDao;
    private final GroupMapper groupMapper;
    private final ExecutorService executorService;
    private final boolean useApi;

    private final MutableLiveData<List<Group>> groupsLiveData;
    private final Map<String, UserApiModel> usersById;
    private final Map<Integer, String> userServerIdByLocalId;
    private final Map<Integer, String> groupServerIdByLocalId;

    @Inject
    public GroupRepository(RestApiService restApiService) {
        this.restApiService = restApiService;

        this.groupDao = null;
        this.groupMapper = null;
        this.executorService = Executors.newSingleThreadExecutor();
        this.useApi = true;

        this.groupsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.usersById = new HashMap<>();
        this.userServerIdByLocalId = new HashMap<>();
        this.groupServerIdByLocalId = new HashMap<>();
    }

    // Legacy constructor kept for existing local-data unit tests.
    public GroupRepository(GroupDao groupDao, GroupMapper groupMapper) {
        this.restApiService = null;

        this.groupDao = groupDao;
        this.groupMapper = groupMapper;
        this.executorService = Executors.newSingleThreadExecutor();
        this.useApi = false;

        this.groupsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.usersById = new HashMap<>();
        this.userServerIdByLocalId = new HashMap<>();
        this.groupServerIdByLocalId = new HashMap<>();
    }

    @Override
    public LiveData<List<Group>> getGroups() {
        if (useApi) {
            refreshGroupsAsync();
            return groupsLiveData;
        }
        return Transformations.map(groupDao.getAllGroupsWithFriends(), groupMapper::mapToDomainList);
    }

    @Override
    public LiveData<Group> getGroupById(int groupId) {
        if (useApi) {
            refreshGroupsAsync();
            return Transformations.map(groupsLiveData, groups -> findGroupByLocalId(groups, groupId));
        }
        return Transformations.map(groupDao.getGroupWithFriendsById(groupId), groupMapper::mapToDomain);
    }

    @Override
    public LiveData<Group> getGroupByServerId(String groupId) {
        if (useApi) {
            refreshGroupsAsync();
            return Transformations.map(groupsLiveData, groups -> findGroupByServerId(groups, groupId));
        }
        return IGroupRepository.super.getGroupByServerId(groupId);
    }

    @Override
    public void updateGroup(Group group) {
        if (!useApi) {
            executorService.execute(() -> groupDao.updateGroup(groupMapper.mapToEntity(group)));
            return;
        }

        executorService.execute(() -> {
            String groupId = resolveGroupServerId(group);
            String actorId = resolveActorId();
            if (isBlank(groupId) || isBlank(actorId)) {
                return;
            }

            UpdateGroupRequestDto request = new UpdateGroupRequestDto();
            request.name = group.getName();
            request.avatarColor = (long) group.getAvatarColor();

            try {
                restApiService.updateGroup(groupId, actorId, request).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void addMember(int groupId, int friendId) {
        if (!useApi) {
            executorService.execute(() -> groupDao.insertGroupFriendCrossRefs(
                    Collections.singletonList(new GroupFriendCrossRef(groupId, friendId))
            ));
            return;
        }

        executorService.execute(() -> {
            String groupServerId = groupServerIdByLocalId.get(groupId);
            String memberServerId = userServerIdByLocalId.get(friendId);
            String actorId = resolveActorId();
            if (isBlank(groupServerId) || isBlank(memberServerId) || isBlank(actorId)) {
                return;
            }

            AddMemberRequestDto request = new AddMemberRequestDto();
            request.memberId = memberServerId;
            request.role = "MEMBER";

            try {
                restApiService.addMember(groupServerId, actorId, request).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void addMemberByServerId(String groupId, int friendId) {
        if (!useApi) {
            IGroupRepository.super.addMemberByServerId(groupId, friendId);
            return;
        }

        executorService.execute(() -> {
            String memberServerId = userServerIdByLocalId.get(friendId);
            String actorId = resolveActorId();
            if (isBlank(groupId) || isBlank(memberServerId) || isBlank(actorId)) {
                return;
            }

            AddMemberRequestDto request = new AddMemberRequestDto();
            request.memberId = memberServerId;
            request.role = "MEMBER";

            try {
                restApiService.addMember(groupId, actorId, request).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void removeMember(int groupId, int friendId) {
        if (!useApi) {
            executorService.execute(() -> groupDao.deleteGroupFriendCrossRef(groupId, friendId));
            return;
        }

        executorService.execute(() -> {
            String groupServerId = groupServerIdByLocalId.get(groupId);
            String memberServerId = userServerIdByLocalId.get(friendId);
            String actorId = resolveActorId();
            if (isBlank(groupServerId) || isBlank(memberServerId) || isBlank(actorId)) {
                return;
            }

            try {
                restApiService.removeMember(groupServerId, memberServerId, actorId).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void removeMemberByServerId(String groupId, int friendId) {
        if (!useApi) {
            IGroupRepository.super.removeMemberByServerId(groupId, friendId);
            return;
        }

        executorService.execute(() -> {
            String memberServerId = userServerIdByLocalId.get(friendId);
            String actorId = resolveActorId();
            if (isBlank(groupId) || isBlank(memberServerId) || isBlank(actorId)) {
                return;
            }

            try {
                restApiService.removeMember(groupId, memberServerId, actorId).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void updateMemberRole(int groupId, int friendId, String role) {
        if (!useApi) {
            return;
        }

        executorService.execute(() -> {
            String groupServerId = groupServerIdByLocalId.get(groupId);
            String memberServerId = userServerIdByLocalId.get(friendId);
            String actorId = resolveActorId();
            String normalizedRole = normalizeRole(role);

            if (isBlank(groupServerId) || isBlank(memberServerId) || isBlank(actorId) || isBlank(normalizedRole)) {
                return;
            }

            UpdateMemberRoleRequestDto request = new UpdateMemberRoleRequestDto();
            request.role = normalizedRole;

            try {
                restApiService.updateMemberRole(groupServerId, memberServerId, actorId, request).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void updateMemberRoleByServerId(String groupId, int friendId, String role) {
        if (!useApi) {
            IGroupRepository.super.updateMemberRoleByServerId(groupId, friendId, role);
            return;
        }

        executorService.execute(() -> {
            String memberServerId = userServerIdByLocalId.get(friendId);
            String actorId = resolveActorId();
            String normalizedRole = normalizeRole(role);

            if (isBlank(groupId) || isBlank(memberServerId) || isBlank(actorId) || isBlank(normalizedRole)) {
                return;
            }

            UpdateMemberRoleRequestDto request = new UpdateMemberRoleRequestDto();
            request.role = normalizedRole;

            try {
                restApiService.updateMemberRole(groupId, memberServerId, actorId, request).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void createGroup(String name, List<Friend> selectedFriends) {
        if (!useApi) {
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
            return;
        }

        executorService.execute(() -> {
            String actorId = resolveActorId();
            if (isBlank(actorId) || isBlank(name)) {
                return;
            }

            refreshUsersCache();

            CreateGroupRequestDto request = new CreateGroupRequestDto();
            request.name = name;
            request.ownerId = actorId;
            request.avatarColor = 0xFF03DAC5L;
            request.memberIds = new ArrayList<>();

            if (selectedFriends != null) {
                for (Friend friend : selectedFriends) {
                    String memberId = userServerIdByLocalId.get(friend.getId());
                    if (!isBlank(memberId) && !request.memberIds.contains(memberId)) {
                        request.memberIds.add(memberId);
                    }
                }
            }

            try {
                restApiService.createGroup(request).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    @Override
    public void leaveGroup(Group group) {
        if (!useApi) {
            executorService.execute(() -> groupDao.deleteGroupById(group.getId()));
            return;
        }
        deleteGroupByServerId(group);
    }

    @Override
    public void disbandGroup(Group group) {
        if (!useApi) {
            executorService.execute(() -> groupDao.deleteGroupById(group.getId()));
            return;
        }
        deleteGroupByServerId(group);
    }
    
    @Override
    public void refreshGroups() {
        refreshGroupsAsync();
    }

    private void deleteGroupByServerId(Group group) {
        executorService.execute(() -> {
            String groupId = resolveGroupServerId(group);
            String actorId = resolveActorId();
            if (isBlank(groupId) || isBlank(actorId)) {
                return;
            }

            try {
                restApiService.deleteGroup(groupId, actorId).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshGroupsSync();
        });
    }

    private void refreshGroupsAsync() {
        if (!useApi) {
            return;
        }
        executorService.execute(this::refreshGroupsSync);
    }

    private synchronized void refreshGroupsSync() {
        if (!useApi) {
            return;
        }

        String actorId = resolveActorId();
        if (isBlank(actorId)) {
            groupsLiveData.postValue(Collections.emptyList());
            return;
        }

        try {
            refreshUsersCache();
            Response<List<GroupApiModel>> response = restApiService.getGroupsByUser(actorId).execute();
            if (!response.isSuccessful() || response.body() == null) {
                return;
            }
            groupsLiveData.postValue(mapGroups(response.body(), actorId));
        } catch (Exception ignored) {
        }
    }

    private void refreshUsersCache() {
        try {
            Response<List<UserApiModel>> response = restApiService.getUsers().execute();
            if (!response.isSuccessful() || response.body() == null) {
                return;
            }

            usersById.clear();
            userServerIdByLocalId.clear();
            for (UserApiModel user : response.body()) {
                if (user == null || isBlank(user.id)) {
                    continue;
                }
                usersById.put(user.id, user);
                userServerIdByLocalId.put(stableId(user.id), user.id);
            }
        } catch (Exception ignored) {
        }
    }

    private List<Group> mapGroups(List<GroupApiModel> groups, String actorId) {
        List<Group> result = new ArrayList<>();
        groupServerIdByLocalId.clear();

        for (GroupApiModel apiGroup : groups) {
            if (apiGroup == null || isBlank(apiGroup.id)) {
                continue;
            }
            int localId = stableId(apiGroup.id);
            groupServerIdByLocalId.put(localId, apiGroup.id);

            List<Friend> members = new ArrayList<>();
            Map<Integer, String> memberRoles = new HashMap<>();
            if (apiGroup.memberIds != null) {
                for (String memberId : apiGroup.memberIds) {
                    if (isBlank(memberId)) {
                        continue;
                    }
                    int memberLocalId = stableId(memberId);
                    UserApiModel user = usersById.get(memberId);
                    if (user != null) {
                        members.add(new Friend(
                                stableId(user.id),
                                user.id,
                                user.name != null ? user.name : user.id,
                                !isBlank(user.avatarLetter)
                                        ? user.avatarLetter
                                        : safeAvatarLetter(user.name != null ? user.name : user.id),
                                user.avatarColor,
                                user.isOnline
                        ));
                    } else {
                        members.add(new Friend(
                                memberLocalId,
                                memberId,
                                memberId,
                                safeAvatarLetter(memberId),
                                0xFF03DAC5,
                                false
                        ));
                    }

                    String role = "MEMBER";
                    if (apiGroup.memberRoles != null && apiGroup.memberRoles.get(memberId) != null) {
                        role = normalizeRole(apiGroup.memberRoles.get(memberId));
                    }
                    if (memberId.equals(apiGroup.ownerId)) {
                        role = "ADMIN";
                    }
                    memberRoles.put(memberLocalId, role);
                }
            }

            result.add(new Group(
                    localId,
                    apiGroup.id,
                    apiGroup.name != null ? apiGroup.name : apiGroup.id,
                    !isBlank(apiGroup.avatarLetter) ? apiGroup.avatarLetter : safeAvatarLetter(apiGroup.name),
                    apiGroup.avatarColor,
                    members,
                    actorId.equals(apiGroup.ownerId),
                    memberRoles
            ));
        }
        return result;
    }

    private String normalizeRole(String role) {
        if (isBlank(role)) {
            return "MEMBER";
        }
        String normalized = role.trim().toUpperCase();
        if ("ADMIN".equals(normalized)) {
            return "ADMIN";
        }
        return "MEMBER";
    }

    private Group findGroupByLocalId(List<Group> groups, int localId) {
        if (groups == null) {
            return null;
        }
        for (Group group : groups) {
            if (group != null && group.getId() == localId) {
                return group;
            }
        }
        return null;
    }

    private Group findGroupByServerId(List<Group> groups, String serverId) {
        if (groups == null || isBlank(serverId)) {
            return null;
        }
        for (Group group : groups) {
            if (group != null && serverId.equals(group.getServerId())) {
                return group;
            }
        }
        return null;
    }

    private String resolveGroupServerId(Group group) {
        if (group != null && !isBlank(group.getServerId())) {
            return group.getServerId();
        }
        if (group != null) {
            return groupServerIdByLocalId.get(group.getId());
        }
        return null;
    }

    private String resolveActorId() {
        try {
            Response<com.bif.app.core.network.dto.auth.AuthStateResponse> response =
                    restApiService.getAuthState().execute();
            if (!response.isSuccessful() || response.body() == null || !response.body().authenticated) {
                return null;
            }
            return response.body().userId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private int stableId(String value) {
        if (value == null) {
            return 0;
        }
        return value.hashCode() & 0x7fffffff;
    }

    private String safeAvatarLetter(String value) {
        if (isBlank(value)) {
            return "G";
        }
        return String.valueOf(value.trim().charAt(0)).toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}