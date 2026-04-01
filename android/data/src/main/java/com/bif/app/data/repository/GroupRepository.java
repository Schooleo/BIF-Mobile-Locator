package com.bif.app.data.repository;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.group.AddMemberRequestDto;
import com.bif.app.core.network.dto.group.CreateGroupRequestDto;
import com.bif.app.core.network.dto.group.GroupApiModel;
import com.bif.app.core.network.dto.group.UpdateGroupRequestDto;
import com.bif.app.core.network.dto.group.UpdateMemberRoleRequestDto;
import com.bif.app.core.network.dto.user.UserApiModel;
import com.bif.app.data.mapper.GroupMapper;
import com.bif.app.data.sync.NetworkMonitor;
import com.bif.app.data.sync.SyncManager;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;


import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Singleton;

import retrofit2.Response;

@Singleton
public class GroupRepository implements IGroupRepository {

    private static final String TAG = "GroupRepository";

    private final RestApiService restApiService;
    private final SyncManager syncManager;
    private final NetworkMonitor networkMonitor;

    private final GroupDao groupDao;
    private final FriendDao friendDao;
    private final GroupMapper groupMapper;
    private final Gson gson;
    private final ExecutorService executorService;
    private final boolean useApi;

    private final MutableLiveData<List<Group>> groupsLiveData;
    private final Map<String, UserApiModel> usersById;
    private final Map<Integer, String> userServerIdByLocalId;
    private final Map<Integer, String> groupServerIdByLocalId;

    @Inject
    public GroupRepository(RestApiService restApiService,
                           GroupDao groupDao,
                           SyncManager syncManager,
                           NetworkMonitor networkMonitor,
                           FriendDao friendDao) {
        this.restApiService = restApiService;
        this.syncManager = syncManager;
        this.networkMonitor = networkMonitor;

        this.groupDao = groupDao;
        this.friendDao = friendDao;
        this.groupMapper = null;
        this.gson = new Gson();
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
        this.syncManager = null;
        this.networkMonitor = null;

        this.groupDao = groupDao;
        this.friendDao = null;
        this.groupMapper = groupMapper;
        this.gson = null;
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
            emitCachedGroupsAsync();
            refreshGroupsAsync();
            return groupsLiveData;
        }
        return Transformations.map(groupDao.getAllGroupsWithFriends(), groupMapper::mapToDomainList);
    }

    @Override
    public LiveData<Group> getGroupById(int groupId) {
        if (useApi) {
            emitCachedGroupsAsync();
            refreshGroupsAsync();
            return Transformations.map(groupsLiveData, groups -> findGroupByLocalId(groups, groupId));
        }
        return Transformations.map(groupDao.getGroupWithFriendsById(groupId), groupMapper::mapToDomain);
    }

    @Override
    public LiveData<Group> getGroupByServerId(String groupId) {
        if (useApi) {
            emitCachedGroupsAsync();
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
                enqueueGroupChange("UPDATE", groupId, request);
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
            String memberServerId = resolveMemberServerId(friendId);
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
                enqueueGroupChange("ADD_MEMBER", groupServerId, request);
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
            String memberServerId = resolveMemberServerId(friendId);
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
                enqueueGroupChange("ADD_MEMBER", groupId, request);
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

        requireOnlineForPolicy("GROUP_REMOVE_MEMBER_REQUIRES_ONLINE");

        executorService.execute(() -> {
            try {
                String groupServerId = groupServerIdByLocalId.get(groupId);
                String memberServerId = resolveMemberServerId(friendId);
                String actorId = resolveActorId();
                if (isBlank(groupServerId) || isBlank(memberServerId) || isBlank(actorId)) {
                    return;
                }

                restApiService.removeMember(groupServerId, memberServerId, actorId).execute();
            } catch (Exception ignored) {
                // Online-only by policy; do not enqueue destructive member removal.
            } catch (Throwable throwable) {
                Log.e(TAG, "removeMember failed unexpectedly", throwable);
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

        requireOnlineForPolicy("GROUP_REMOVE_MEMBER_REQUIRES_ONLINE");

        executorService.execute(() -> {
            try {
                String memberServerId = resolveMemberServerId(friendId);
                String actorId = resolveActorId();
                if (isBlank(groupId) || isBlank(memberServerId) || isBlank(actorId)) {
                    return;
                }

                restApiService.removeMember(groupId, memberServerId, actorId).execute();
            } catch (Exception ignored) {
                // Online-only by policy; do not enqueue destructive member removal.
            } catch (Throwable throwable) {
                Log.e(TAG, "removeMemberByServerId failed unexpectedly", throwable);
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
            String memberServerId = resolveMemberServerId(friendId);
            String actorId = resolveActorId();
            String normalizedRole = normalizeRole(role);

            if (isBlank(groupServerId) || isBlank(memberServerId) || isBlank(normalizedRole)) {
                return;
            }

            if (isBlank(actorId)) {
                Map<String, String> payload = new HashMap<>();
                payload.put("memberId", memberServerId);
                payload.put("role", normalizedRole);
                enqueueGroupChange("UPDATE_MEMBER_ROLE", groupServerId, payload);
                return;
            }

            UpdateMemberRoleRequestDto request = new UpdateMemberRoleRequestDto();
            request.role = normalizedRole;

            try {
                restApiService.updateMemberRole(groupServerId, memberServerId, actorId, request).execute();
            } catch (Exception ignored) {
                Map<String, String> payload = new HashMap<>();
                payload.put("memberId", memberServerId);
                payload.put("role", normalizedRole);
                enqueueGroupChange("UPDATE_MEMBER_ROLE", groupServerId,
                        payload);
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
            String memberServerId = resolveMemberServerId(friendId);
            String actorId = resolveActorId();
            String normalizedRole = normalizeRole(role);

            if (isBlank(groupId) || isBlank(memberServerId) || isBlank(normalizedRole)) {
                return;
            }

            if (isBlank(actorId)) {
                Map<String, String> payload = new HashMap<>();
                payload.put("memberId", memberServerId);
                payload.put("role", normalizedRole);
                enqueueGroupChange("UPDATE_MEMBER_ROLE", groupId, payload);
                return;
            }

            UpdateMemberRoleRequestDto request = new UpdateMemberRoleRequestDto();
            request.role = normalizedRole;

            try {
                restApiService.updateMemberRole(groupId, memberServerId, actorId, request).execute();
            } catch (Exception ignored) {
                Map<String, String> payload = new HashMap<>();
                payload.put("memberId", memberServerId);
                payload.put("role", normalizedRole);
                enqueueGroupChange("UPDATE_MEMBER_ROLE", groupId, payload);
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

        requireOnlineForPolicy("GROUP_CREATE_REQUIRES_ONLINE");

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
                    if (friend == null) {
                        continue;
                    }

                    String memberId = null;
                    if (!isBlank(friend.getServerUserId())) {
                        memberId = friend.getServerUserId().trim();
                    }
                    if (isBlank(memberId)) {
                        memberId = userServerIdByLocalId.get(friend.getId());
                    }
                    if (isBlank(memberId)) {
                        memberId = resolveUserIdByName(friend.getName());
                    }

                    if (!isBlank(memberId) && !request.memberIds.contains(memberId)) {
                        request.memberIds.add(memberId);
                    }
                }
            }

            try {
                Response<GroupApiModel> createResponse = restApiService.createGroup(request).execute();
                if (createResponse.isSuccessful() && createResponse.body() != null) {
                    List<Group> mappedCreated = mapGroups(Collections.singletonList(createResponse.body()), actorId);
                    if (!mappedCreated.isEmpty()) {
                        Group created = mappedCreated.get(0);
                        List<Group> current = groupsLiveData.getValue();
                        List<Group> merged = new ArrayList<>();
                        if (current != null) {
                            for (Group group : current) {
                                if (group == null) {
                                    continue;
                                }
                                if (!created.getServerId().equals(group.getServerId())) {
                                    merged.add(group);
                                }
                            }
                        }
                        merged.add(0, created);
                        groupsLiveData.postValue(merged);
                    }
                }
            } catch (Exception ignored) {
                enqueueGroupChange("CREATE", "local-" + UUID.randomUUID(),
                        request);
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
        requireOnlineForPolicy("GROUP_DELETE_REQUIRES_ONLINE");

        executorService.execute(() -> {
            String groupId = resolveGroupServerId(group);
            String actorId = resolveActorId();
            if (isBlank(groupId) || isBlank(actorId)) {
                return;
            }

            try {
                restApiService.deleteGroup(groupId, actorId).execute();
            } catch (Exception ignored) {
                enqueueGroupChange("DELETE", groupId, null);
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
            emitCachedGroupsSync();
            return;
        }

        try {
            refreshUsersCache();
            Response<List<GroupApiModel>> response = restApiService.getGroupsByUser(actorId).execute();
            if (!response.isSuccessful() || response.body() == null) {
                emitCachedGroupsSync();
                return;
            }
            List<GroupApiModel> apiGroups = response.body();
            groupsLiveData.postValue(mapGroups(apiGroups, actorId));
            cacheGroups(apiGroups, actorId);
        } catch (Exception ignored) {
            emitCachedGroupsSync();
        }
    }

    private void cacheGroups(List<GroupApiModel> apiGroups, String actorId) {
        if (gson == null) {
            return;
        }

        List<GroupEntity> existing = groupDao.getAllGroupsSync();
        Map<String, Integer> existingIdsByServerId = new HashMap<>();
        for (GroupEntity entity : existing) {
            if (entity != null && !isBlank(entity.getServerId())) {
                existingIdsByServerId.put(entity.getServerId(), entity.getId());
            }
        }

        List<GroupEntity> replacement = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (GroupApiModel apiGroup : apiGroups) {
            if (apiGroup == null || isBlank(apiGroup.id)) {
                continue;
            }

            int localId = existingIdsByServerId.containsKey(apiGroup.id)
                    ? existingIdsByServerId.get(apiGroup.id)
                    : stableId(apiGroup.id);

            GroupEntity entity = new GroupEntity(
                    localId,
                    apiGroup.id,
                    apiGroup.name != null ? apiGroup.name : apiGroup.id,
                    !isBlank(apiGroup.avatarLetter) ? apiGroup.avatarLetter : safeAvatarLetter(apiGroup.name),
                    apiGroup.avatarColor,
                    actorId.equals(apiGroup.ownerId),
                    apiGroup.ownerId,
                    gson.toJson(apiGroup.memberIds != null ? apiGroup.memberIds : Collections.emptyList()),
                    gson.toJson(apiGroup.memberRoles != null ? apiGroup.memberRoles : Collections.emptyMap()),
                    0L,
                    false,
                    now
            );
            replacement.add(entity);
        }

        groupDao.replaceAllGroups(replacement);
    }

    private void emitCachedGroupsAsync() {
        executorService.execute(this::emitCachedGroupsSync);
    }

    private void emitCachedGroupsSync() {
        List<GroupEntity> cachedEntities = groupDao.getAllGroupsSync();
        if (cachedEntities == null || cachedEntities.isEmpty()) {
            return;
        }
        groupsLiveData.postValue(mapCachedGroups(cachedEntities));
    }

    private List<Group> mapCachedGroups(List<GroupEntity> cachedEntities) {
        List<Group> cachedGroups = new ArrayList<>();
        populateUsersFromFriendCache();
        groupServerIdByLocalId.clear();

        for (GroupEntity entity : cachedEntities) {
            if (entity == null || isBlank(entity.getServerId()) || entity.isDeleted()) {
                continue;
            }

            String serverId = entity.getServerId();
            int localId = entity.getId() > 0 ? entity.getId() : stableId(serverId);
            groupServerIdByLocalId.put(localId, serverId);

            List<String> memberIds = parseMemberIds(entity.getMemberIdsJson());
            Map<String, String> rolesByServerId = parseMemberRoles(entity.getMemberRolesJson());
            List<Friend> members = new ArrayList<>();
            Map<Integer, String> memberRoles = new HashMap<>();

            for (String memberId : memberIds) {
                if (isBlank(memberId)) {
                    continue;
                }

                int memberLocalId = stableId(memberId);
                userServerIdByLocalId.put(memberLocalId, memberId);

                UserApiModel cachedUser = usersById.get(memberId);
                String friendName = cachedUser != null && !isBlank(cachedUser.name)
                        ? cachedUser.name
                        : memberId;
                String avatarLetter = cachedUser != null && !isBlank(cachedUser.avatarLetter)
                        ? cachedUser.avatarLetter
                        : safeAvatarLetter(friendName);
                int avatarColor = cachedUser != null ? cachedUser.avatarColor : 0xFF03DAC5;
                boolean isOnline = cachedUser != null && cachedUser.isOnline;

                members.add(new Friend(
                        memberLocalId,
                        memberId,
                        friendName,
                        avatarLetter,
                        avatarColor,
                        isOnline
                ));

                String role = normalizeRole(rolesByServerId.get(memberId));
                if (memberId.equals(entity.getOwnerId())) {
                    role = "ADMIN";
                }
                memberRoles.put(memberLocalId, role);
            }

            cachedGroups.add(new Group(
                    localId,
                    serverId,
                    entity.getName(),
                    entity.getAvatarLetter(),
                    entity.getAvatarColor(),
                    members,
                    entity.isOwner(),
                    memberRoles
            ));
        }

        return cachedGroups;
    }

    private List<String> parseMemberIds(String json) {
        if (gson == null || isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            Type listType = new TypeToken<List<String>>() { }.getType();
            List<String> parsed = gson.fromJson(json, listType);
            return parsed != null ? parsed : Collections.emptyList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Map<String, String> parseMemberRoles(String json) {
        if (gson == null || isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            Type mapType = new TypeToken<Map<String, String>>() { }.getType();
            Map<String, String> parsed = gson.fromJson(json, mapType);
            return parsed != null ? parsed : Collections.emptyMap();
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private void refreshUsersCache() {
        try {
            Response<List<UserApiModel>> response = restApiService.getUsers().execute();
            if (!response.isSuccessful() || response.body() == null) {
                populateUsersFromFriendCache();
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
            populateUsersFromFriendCache();
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

    private String resolveMemberServerId(int friendId) {
        String memberServerId = userServerIdByLocalId.get(friendId);
        if (!isBlank(memberServerId)) {
            return memberServerId;
        }

        memberServerId = resolveMemberServerIdFromUsers(friendId);
        if (!isBlank(memberServerId)) {
            return memberServerId;
        }

        refreshUsersCache();
        memberServerId = userServerIdByLocalId.get(friendId);
        if (!isBlank(memberServerId)) {
            return memberServerId;
        }
        return resolveMemberServerIdFromUsers(friendId);
    }

    private String resolveMemberServerIdFromUsers(int friendId) {
        for (Map.Entry<String, UserApiModel> entry : usersById.entrySet()) {
            String serverId = entry.getKey();
            if (!isBlank(serverId) && stableId(serverId) == friendId) {
                return serverId;
            }
        }
        return null;
    }

    private void populateUsersFromFriendCache() {
        if (friendDao == null) {
            return;
        }

        List<FriendEntity> cachedFriends;
        try {
            cachedFriends = friendDao.getAllFriendsSync();
        } catch (Exception ignored) {
            return;
        }

        if (cachedFriends == null || cachedFriends.isEmpty()) {
            return;
        }

        for (FriendEntity friend : cachedFriends) {
            if (friend == null || isBlank(friend.serverUserId)) {
                continue;
            }

            String serverId = friend.serverUserId.trim();
            UserApiModel cachedUser = usersById.get(serverId);
            if (cachedUser == null) {
                cachedUser = new UserApiModel();
                cachedUser.id = serverId;
            }

            cachedUser.name = !isBlank(friend.name) ? friend.name : serverId;
            cachedUser.avatarLetter = !isBlank(friend.avatarLetter)
                    ? friend.avatarLetter
                    : safeAvatarLetter(cachedUser.name);
            cachedUser.avatarColor = friend.avatarColor;
            cachedUser.isOnline = friend.isOnline;

            usersById.put(serverId, cachedUser);
            userServerIdByLocalId.put(stableId(serverId), serverId);
            userServerIdByLocalId.put(friend.id, serverId);
        }
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

    private String resolveUserIdByName(String name) {
        if (isBlank(name)) {
            return null;
        }

        String normalizedName = name.trim();
        for (UserApiModel user : usersById.values()) {
            if (user == null || isBlank(user.id) || isBlank(user.name)) {
                continue;
            }
            if (normalizedName.equalsIgnoreCase(user.name.trim())) {
                return user.id.trim();
            }
        }
        return null;
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

    private void requireOnlineForPolicy(String errorCode) {
        if (networkMonitor != null && !networkMonitor.isOnline()) {
            throw new IllegalStateException(errorCode);
        }
    }

    private void enqueueGroupChange(String operation, String entityId,
                                    Object payload) {
        if (syncManager == null) {
            return;
        }
        syncManager.enqueueChange("group", entityId, operation,
                UUID.randomUUID().toString(), payload);
    }
}
