package com.bif.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;

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
import com.bif.app.core.network.dto.auth.AuthStateResponse;
import com.bif.app.data.mapper.GroupMapper;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.GroupDao;
import com.bif.app.data.source.local.SocialActionQueueDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupFriendCrossRef;
import com.bif.app.data.source.local.entity.SocialActionQueueEntity;
import com.bif.app.data.sync.NetworkMonitor;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Response;

@Singleton
public class GroupRepository implements IGroupRepository {

    private static final String QUEUE_SCOPE = "GROUP";
    private static final String ACTION_CREATE_GROUP = "CREATE_GROUP";
    private static final String ACTION_UPDATE_GROUP = "UPDATE_GROUP";
    private static final String ACTION_ADD_MEMBER = "ADD_MEMBER";
    private static final String ACTION_REMOVE_MEMBER = "REMOVE_MEMBER";
    private static final String ACTION_UPDATE_MEMBER_ROLE = "UPDATE_MEMBER_ROLE";
    private static final String ACTION_DELETE_GROUP = "DELETE_GROUP";
    private static final int MAX_RETRY_COUNT = 5;

    private static final String CACHE_PREF = "SOCIAL_GROUP_CACHE";
    private static final String CACHE_KEY_GROUPS = "groups_json";

    private final RestApiService restApiService;
    private final GroupDao groupDao;
    private final GroupMapper groupMapper;
    private final FriendDao friendDao;
    private final SocialActionQueueDao socialActionQueueDao;
    private final NetworkMonitor networkMonitor;
    private final ExecutorService executorService;
    private final boolean useApi;
    private final Gson gson;

    private final MutableLiveData<List<Group>> groupsLiveData;
    private final Map<String, UserApiModel> usersById;
    private final Map<Integer, String> userServerIdByLocalId;
    private final Map<Integer, String> groupServerIdByLocalId;
    private final SharedPreferences cachePrefs;

    private volatile String cachedActorId;

    @Inject
    public GroupRepository(RestApiService restApiService,
                           GroupDao groupDao,
                           GroupMapper groupMapper,
                           FriendDao friendDao,
                           SocialActionQueueDao socialActionQueueDao,
                           NetworkMonitor networkMonitor,
                           @ApplicationContext Context appContext) {
        this.restApiService = restApiService;
        this.groupDao = groupDao;
        this.groupMapper = groupMapper;
        this.friendDao = friendDao;
        this.socialActionQueueDao = socialActionQueueDao;
        this.networkMonitor = networkMonitor;
        this.executorService = Executors.newSingleThreadExecutor();
        this.useApi = true;

        this.groupsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.usersById = new HashMap<>();
        this.userServerIdByLocalId = new HashMap<>();
        this.groupServerIdByLocalId = new HashMap<>();
        this.gson = new Gson();
        this.cachePrefs = appContext.getSharedPreferences(
                CACHE_PREF, Context.MODE_PRIVATE);

        loadCachedGroupsIntoLiveData();

        if (this.networkMonitor != null) {
            this.networkMonitor.observeConnectivity().observeForever(
                    connected -> {
                        if (Boolean.TRUE.equals(connected)) {
                            refreshGroupsAsync();
                        }
                    }
            );
        }
    }

    // Legacy constructor kept for existing local-data unit tests.
    public GroupRepository(GroupDao groupDao, GroupMapper groupMapper) {
        this.restApiService = null;
        this.groupDao = groupDao;
        this.groupMapper = groupMapper;
        this.friendDao = null;
        this.socialActionQueueDao = null;
        this.networkMonitor = null;
        this.executorService = Executors.newSingleThreadExecutor();
        this.useApi = false;

        this.groupsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.usersById = new HashMap<>();
        this.userServerIdByLocalId = new HashMap<>();
        this.groupServerIdByLocalId = new HashMap<>();
        this.gson = new Gson();
        this.cachePrefs = null;
    }

    @Override
    public LiveData<List<Group>> getGroups() {
        if (useApi) {
            refreshGroupsAsync();
            return groupsLiveData;
        }
        return Transformations.map(groupDao.getAllGroupsWithFriends(),
                groupMapper::mapToDomainList);
    }

    @Override
    public LiveData<Group> getGroupById(int groupId) {
        if (useApi) {
            refreshGroupsAsync();
            return Transformations.map(groupsLiveData,
                    groups -> findGroupByLocalId(groups, groupId));
        }
        return Transformations.map(groupDao.getGroupWithFriendsById(groupId),
                groupMapper::mapToDomain);
    }

    @Override
    public LiveData<Group> getGroupByServerId(String groupId) {
        if (useApi) {
            refreshGroupsAsync();
            return Transformations.map(groupsLiveData,
                    groups -> findGroupByServerId(groups, groupId));
        }
        return IGroupRepository.super.getGroupByServerId(groupId);
    }

    @Override
    public void updateGroup(Group group) {
        if (!useApi) {
            executorService.execute(() ->
                    groupDao.updateGroup(groupMapper.mapToEntity(group)));
            return;
        }

        executorService.execute(() -> {
            if (group == null) {
                return;
            }

            Group updated = updateLocalGroupName(group);
            if (updated == null || isBlank(updated.getServerId())) {
                return;
            }

            enqueueAction(ACTION_UPDATE_GROUP,
                    new UpdateGroupPayload(
                            updated.getServerId(),
                            updated.getName(),
                            updated.getAvatarColor()
                    ));
            syncIfOnline();
        });
    }

    @Override
    public void addMember(int groupId, int friendId) {
        if (!useApi) {
            executorService.execute(() -> groupDao.insertGroupFriendCrossRefs(
                    Collections.singletonList(
                            new GroupFriendCrossRef(groupId, friendId))));
            return;
        }

        executorService.execute(() -> {
            Group target = findGroupByLocalId(groupsLiveData.getValue(), groupId);
            if (target == null) {
                return;
            }

            String memberServerId = resolveMemberServerId(friendId);
            if (isBlank(memberServerId)) {
                return;
            }

            addMemberLocally(target.getServerId(), friendId,
                    memberServerId, "MEMBER");
            enqueueAction(ACTION_ADD_MEMBER,
                    new GroupMemberPayload(
                            target.getServerId(),
                            memberServerId,
                            "MEMBER"
                    ));
            syncIfOnline();
        });
    }

    @Override
    public void addMemberByServerId(String groupId, int friendId) {
        if (!useApi) {
            IGroupRepository.super.addMemberByServerId(groupId, friendId);
            return;
        }

        executorService.execute(() -> {
            if (isBlank(groupId)) {
                return;
            }

            String memberServerId = resolveMemberServerId(friendId);
            if (isBlank(memberServerId)) {
                return;
            }

            addMemberLocally(groupId, friendId, memberServerId, "MEMBER");
            enqueueAction(ACTION_ADD_MEMBER,
                    new GroupMemberPayload(groupId, memberServerId,
                            "MEMBER"));
            syncIfOnline();
        });
    }

    @Override
    public void removeMember(int groupId, int friendId) {
        if (!useApi) {
            executorService.execute(() ->
                    groupDao.deleteGroupFriendCrossRef(groupId, friendId));
            return;
        }

        executorService.execute(() -> {
            Group target = findGroupByLocalId(groupsLiveData.getValue(), groupId);
            if (target == null || isBlank(target.getServerId())) {
                return;
            }

            String memberServerId = resolveMemberServerId(friendId);
            if (isBlank(memberServerId)) {
                return;
            }

            removeMemberLocally(target.getServerId(), friendId);
            enqueueAction(ACTION_REMOVE_MEMBER,
                    new GroupMemberPayload(target.getServerId(),
                            memberServerId, null));
            syncIfOnline();
        });
    }

    @Override
    public void removeMemberByServerId(String groupId, int friendId) {
        if (!useApi) {
            IGroupRepository.super.removeMemberByServerId(groupId, friendId);
            return;
        }

        executorService.execute(() -> {
            String memberServerId = resolveMemberServerId(friendId);
            if (isBlank(groupId) || isBlank(memberServerId)) {
                return;
            }

            removeMemberLocally(groupId, friendId);
            enqueueAction(ACTION_REMOVE_MEMBER,
                    new GroupMemberPayload(groupId, memberServerId, null));
            syncIfOnline();
        });
    }

    @Override
    public void updateMemberRole(int groupId, int friendId, String role) {
        if (!useApi) {
            return;
        }

        executorService.execute(() -> {
            Group target = findGroupByLocalId(groupsLiveData.getValue(), groupId);
            if (target == null || isBlank(target.getServerId())) {
                return;
            }

            String memberServerId = resolveMemberServerId(friendId);
            String normalizedRole = normalizeRole(role);
            if (isBlank(memberServerId) || isBlank(normalizedRole)) {
                return;
            }

            updateMemberRoleLocally(target.getServerId(), friendId,
                    normalizedRole);
            enqueueAction(ACTION_UPDATE_MEMBER_ROLE,
                    new GroupMemberPayload(target.getServerId(),
                            memberServerId, normalizedRole));
            syncIfOnline();
        });
    }

    @Override
    public void updateMemberRoleByServerId(String groupId, int friendId,
                                           String role) {
        if (!useApi) {
            IGroupRepository.super.updateMemberRoleByServerId(
                    groupId, friendId, role);
            return;
        }

        executorService.execute(() -> {
            String memberServerId = resolveMemberServerId(friendId);
            String normalizedRole = normalizeRole(role);
            if (isBlank(groupId) || isBlank(memberServerId)
                    || isBlank(normalizedRole)) {
                return;
            }

            updateMemberRoleLocally(groupId, friendId, normalizedRole);
            enqueueAction(ACTION_UPDATE_MEMBER_ROLE,
                    new GroupMemberPayload(groupId, memberServerId,
                            normalizedRole));
            syncIfOnline();
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
                        crossRefs.add(new GroupFriendCrossRef((int) newGroupId,
                                friend.getId()));
                    }
                    groupDao.insertGroupFriendCrossRefs(crossRefs);
                }
            });
            return;
        }

        executorService.execute(() -> {
            if (isBlank(name)) {
                return;
            }

            String tempGroupId = "local-" + UUID.randomUUID();
            List<String> memberIds = new ArrayList<>();
            if (selectedFriends != null) {
                for (Friend friend : selectedFriends) {
                    if (friend == null) {
                        continue;
                    }
                    String memberId = !isBlank(friend.getServerUserId())
                            ? friend.getServerUserId()
                            : resolveMemberServerId(friend.getId());
                    if (!isBlank(memberId) && !memberIds.contains(memberId)) {
                        memberIds.add(memberId);
                    }
                }
            }

            addGroupLocally(tempGroupId, name, 0xFF03DAC5,
                    selectedFriends);
            enqueueAction(ACTION_CREATE_GROUP,
                    new CreateGroupPayload(tempGroupId, name,
                            0xFF03DAC5, memberIds));
            syncIfOnline();
        });
    }

    @Override
    public void leaveGroup(Group group) {
        if (!useApi) {
            executorService.execute(() -> groupDao.deleteGroupById(group.getId()));
            return;
        }
        deleteGroupLocallyAndQueue(group);
    }

    @Override
    public void disbandGroup(Group group) {
        if (!useApi) {
            executorService.execute(() -> groupDao.deleteGroupById(group.getId()));
            return;
        }
        deleteGroupLocallyAndQueue(group);
    }

    @Override
    public void refreshGroups() {
        refreshGroupsAsync();
    }

    private void deleteGroupLocallyAndQueue(Group group) {
        executorService.execute(() -> {
            if (group == null || isBlank(group.getServerId())) {
                return;
            }
            removeGroupLocally(group.getServerId());
            enqueueAction(ACTION_DELETE_GROUP,
                    new DeleteGroupPayload(group.getServerId()));
            syncIfOnline();
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

        if (!isOnline()) {
            loadCachedGroupsIntoLiveData();
            return;
        }

        replayQueuedActions();

        String actorId = resolveActorId();
        if (isBlank(actorId)) {
            loadCachedGroupsIntoLiveData();
            return;
        }

        try {
            refreshUsersCache();
            Response<List<GroupApiModel>> response = restApiService
                    .getGroupsByUser(actorId).execute();
            if (!response.isSuccessful() || response.body() == null) {
                loadCachedGroupsIntoLiveData();
                return;
            }

            List<Group> mapped = mapGroups(response.body(), actorId);
            persistCachedGroups(mapped);
            groupsLiveData.postValue(mapped);
        } catch (Exception ignored) {
            loadCachedGroupsIntoLiveData();
        }
    }

    private void syncIfOnline() {
        if (!isOnline()) {
            return;
        }
        replayQueuedActions();
        refreshGroupsSync();
    }

    private void replayQueuedActions() {
        if (socialActionQueueDao == null || !isOnline()) {
            return;
        }

        socialActionQueueDao.resetInFlight();
        List<SocialActionQueueEntity> pending = socialActionQueueDao
                .getPendingByScope(QUEUE_SCOPE);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (SocialActionQueueEntity entry : pending) {
            entry.status = "IN_FLIGHT";
            socialActionQueueDao.update(entry);

            QueueOutcome outcome = executeQueuedAction(entry);
            if (outcome == QueueOutcome.SUCCESS) {
                socialActionQueueDao.remove(entry.id);
                continue;
            }

            if (outcome == QueueOutcome.DEFERRED) {
                entry.status = "PENDING";
                socialActionQueueDao.update(entry);
                continue;
            }

            entry.retryCount++;
            entry.status = entry.retryCount > MAX_RETRY_COUNT
                    ? "FAILED" : "PENDING";
            socialActionQueueDao.update(entry);
        }
    }

    private QueueOutcome executeQueuedAction(SocialActionQueueEntity entry) {
        String actorId = resolveActorId();
        if (isBlank(actorId)) {
            return QueueOutcome.FAILED;
        }

        try {
            if (ACTION_CREATE_GROUP.equals(entry.actionType)) {
                CreateGroupPayload payload = gson.fromJson(entry.payload,
                        CreateGroupPayload.class);
                if (payload == null || isBlank(payload.name)) {
                    return QueueOutcome.SUCCESS;
                }

                CreateGroupRequestDto request = new CreateGroupRequestDto();
                request.name = payload.name;
                request.ownerId = actorId;
                request.avatarColor = (long) payload.avatarColor;
                request.memberIds = payload.memberIds != null
                        ? new ArrayList<>(payload.memberIds)
                        : new ArrayList<>();

                Response<GroupApiModel> response = restApiService
                        .createGroup(request).execute();
                if (!response.isSuccessful() || response.body() == null
                        || isBlank(response.body().id)) {
                    return QueueOutcome.FAILED;
                }

                replaceLocalGroupId(payload.tempGroupId,
                        response.body().id);
                return QueueOutcome.SUCCESS;
            }

            if (ACTION_UPDATE_GROUP.equals(entry.actionType)) {
                UpdateGroupPayload payload = gson.fromJson(entry.payload,
                        UpdateGroupPayload.class);
                if (payload == null || isBlank(payload.groupId)) {
                    return QueueOutcome.SUCCESS;
                }
                if (isLocalPlaceholder(payload.groupId)) {
                    return QueueOutcome.DEFERRED;
                }

                UpdateGroupRequestDto request = new UpdateGroupRequestDto();
                request.name = payload.name;
                request.avatarColor = (long) payload.avatarColor;

                Response<GroupApiModel> response = restApiService
                        .updateGroup(payload.groupId, actorId, request)
                        .execute();
                return response.isSuccessful()
                        ? QueueOutcome.SUCCESS : QueueOutcome.FAILED;
            }

            if (ACTION_ADD_MEMBER.equals(entry.actionType)) {
                GroupMemberPayload payload = gson.fromJson(entry.payload,
                        GroupMemberPayload.class);
                if (payload == null || isBlank(payload.groupId)
                        || isBlank(payload.memberId)) {
                    return QueueOutcome.SUCCESS;
                }
                if (isLocalPlaceholder(payload.groupId)) {
                    return QueueOutcome.DEFERRED;
                }

                AddMemberRequestDto request = new AddMemberRequestDto();
                request.memberId = payload.memberId;
                request.role = normalizeRole(payload.role);

                Response<GroupApiModel> response = restApiService
                        .addMember(payload.groupId, actorId, request)
                        .execute();
                return response.isSuccessful()
                        ? QueueOutcome.SUCCESS : QueueOutcome.FAILED;
            }

            if (ACTION_REMOVE_MEMBER.equals(entry.actionType)) {
                GroupMemberPayload payload = gson.fromJson(entry.payload,
                        GroupMemberPayload.class);
                if (payload == null || isBlank(payload.groupId)
                        || isBlank(payload.memberId)) {
                    return QueueOutcome.SUCCESS;
                }
                if (isLocalPlaceholder(payload.groupId)) {
                    return QueueOutcome.DEFERRED;
                }

                Response<GroupApiModel> response = restApiService
                        .removeMember(payload.groupId, payload.memberId,
                                actorId)
                        .execute();
                return response.isSuccessful()
                        ? QueueOutcome.SUCCESS : QueueOutcome.FAILED;
            }

            if (ACTION_UPDATE_MEMBER_ROLE.equals(entry.actionType)) {
                GroupMemberPayload payload = gson.fromJson(entry.payload,
                        GroupMemberPayload.class);
                if (payload == null || isBlank(payload.groupId)
                        || isBlank(payload.memberId)) {
                    return QueueOutcome.SUCCESS;
                }
                if (isLocalPlaceholder(payload.groupId)) {
                    return QueueOutcome.DEFERRED;
                }

                UpdateMemberRoleRequestDto request =
                        new UpdateMemberRoleRequestDto();
                request.role = normalizeRole(payload.role);

                Response<GroupApiModel> response = restApiService
                        .updateMemberRole(payload.groupId,
                                payload.memberId, actorId, request)
                        .execute();
                return response.isSuccessful()
                        ? QueueOutcome.SUCCESS : QueueOutcome.FAILED;
            }

            if (ACTION_DELETE_GROUP.equals(entry.actionType)) {
                DeleteGroupPayload payload = gson.fromJson(entry.payload,
                        DeleteGroupPayload.class);
                if (payload == null || isBlank(payload.groupId)) {
                    return QueueOutcome.SUCCESS;
                }
                if (isLocalPlaceholder(payload.groupId)) {
                    return QueueOutcome.SUCCESS;
                }

                Response<Void> response = restApiService
                        .deleteGroup(payload.groupId, actorId).execute();
                return response.isSuccessful()
                        ? QueueOutcome.SUCCESS : QueueOutcome.FAILED;
            }

            return QueueOutcome.SUCCESS;
        } catch (Exception ignored) {
            return QueueOutcome.FAILED;
        }
    }

    private void enqueueAction(String actionType, Object payload) {
        if (socialActionQueueDao == null) {
            return;
        }

        SocialActionQueueEntity entry = new SocialActionQueueEntity();
        entry.scope = QUEUE_SCOPE;
        entry.actionType = actionType;
        entry.payload = payload == null ? null : gson.toJson(payload);
        entry.status = "PENDING";
        entry.retryCount = 0;
        entry.createdAt = System.currentTimeMillis();
        socialActionQueueDao.enqueue(entry);
    }

    private void refreshUsersCache() {
        if (!isOnline()) {
            return;
        }

        try {
            Response<List<UserApiModel>> response = restApiService.getUsers()
                    .execute();
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

    private List<Group> mapGroups(List<GroupApiModel> groups,
                                  String actorId) {
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
                                memberLocalId,
                                user.id,
                                user.name != null ? user.name : user.id,
                                !isBlank(user.avatarLetter)
                                        ? user.avatarLetter
                                        : safeAvatarLetter(
                                        user.name != null
                                                ? user.name : user.id),
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
                    if (apiGroup.memberRoles != null
                            && apiGroup.memberRoles.get(memberId) != null) {
                        role = normalizeRole(apiGroup.memberRoles
                                .get(memberId));
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
                    !isBlank(apiGroup.avatarLetter)
                            ? apiGroup.avatarLetter
                            : safeAvatarLetter(apiGroup.name),
                    apiGroup.avatarColor,
                    members,
                    actorId.equals(apiGroup.ownerId),
                    memberRoles
            ));
        }

        return result;
    }

    private Group updateLocalGroupName(Group group) {
        List<Group> groups = getCachedGroups();
        if (groups.isEmpty()) {
            groups = groupsLiveData.getValue() != null
                    ? new ArrayList<>(groupsLiveData.getValue())
                    : new ArrayList<>();
        }

        Group updated = null;
        for (int i = 0; i < groups.size(); i++) {
            Group item = groups.get(i);
            if (item == null || !sameGroup(item, group)) {
                continue;
            }

            updated = new Group(
                    stableId(item.getServerId()),
                    item.getServerId(),
                    group.getName(),
                    group.getAvatarLetter(),
                    group.getAvatarColor(),
                    item.getMembers(),
                    item.isOwner(),
                    item.getMemberRoles() != null
                            ? new HashMap<>(item.getMemberRoles())
                            : null
            );
            groups.set(i, updated);
            break;
        }

        if (updated != null) {
            persistCachedGroups(groups);
            groupsLiveData.postValue(groups);
        }
        return updated;
    }

    private void addGroupLocally(String serverId, String name,
                                 int avatarColor,
                                 List<Friend> selectedFriends) {
        List<Group> groups = getCachedGroups();
        List<Friend> members = selectedFriends != null
                ? new ArrayList<>(selectedFriends)
                : new ArrayList<>();

        Map<Integer, String> memberRoles = new HashMap<>();
        for (Friend friend : members) {
            if (friend != null) {
                memberRoles.put(friend.getId(), "MEMBER");
            }
        }

        Group localGroup = new Group(
                stableId(serverId),
                serverId,
                name,
                safeAvatarLetter(name),
                avatarColor,
                members,
                true,
                memberRoles
        );
        groups.add(localGroup);

        persistCachedGroups(groups);
        groupsLiveData.postValue(groups);
    }

    private void removeGroupLocally(String serverId) {
        if (isBlank(serverId)) {
            return;
        }

        List<Group> groups = getCachedGroups();
        groups.removeIf(group -> group != null
                && serverId.equals(group.getServerId()));
        persistCachedGroups(groups);
        groupsLiveData.postValue(groups);
    }

    private void addMemberLocally(String groupServerId,
                                  int memberLocalId,
                                  String memberServerId,
                                  String role) {
        List<Group> groups = getCachedGroups();
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group == null
                    || !groupServerId.equals(group.getServerId())) {
                continue;
            }

            List<Friend> members = group.getMembers() != null
                    ? new ArrayList<>(group.getMembers())
                    : new ArrayList<>();
            boolean exists = false;
            for (Friend member : members) {
                if (member != null
                        && memberLocalId == member.getId()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                members.add(resolveMemberPreview(memberLocalId,
                        memberServerId));
            }

            Map<Integer, String> memberRoles = group.getMemberRoles() != null
                    ? new HashMap<>(group.getMemberRoles())
                    : new HashMap<>();
            memberRoles.put(memberLocalId, normalizeRole(role));

            groups.set(i, new Group(
                    stableId(group.getServerId()),
                    group.getServerId(),
                    group.getName(),
                    group.getAvatarLetter(),
                    group.getAvatarColor(),
                    members,
                    group.isOwner(),
                    memberRoles
            ));
            break;
        }

        persistCachedGroups(groups);
        groupsLiveData.postValue(groups);
    }

    private void removeMemberLocally(String groupServerId, int memberLocalId) {
        List<Group> groups = getCachedGroups();
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group == null
                    || !groupServerId.equals(group.getServerId())) {
                continue;
            }

            List<Friend> members = group.getMembers() != null
                    ? new ArrayList<>(group.getMembers())
                    : new ArrayList<>();
            members.removeIf(member -> member != null
                    && member.getId() == memberLocalId);

            Map<Integer, String> memberRoles = group.getMemberRoles() != null
                    ? new HashMap<>(group.getMemberRoles())
                    : new HashMap<>();
            memberRoles.remove(memberLocalId);

            groups.set(i, new Group(
                    stableId(group.getServerId()),
                    group.getServerId(),
                    group.getName(),
                    group.getAvatarLetter(),
                    group.getAvatarColor(),
                    members,
                    group.isOwner(),
                    memberRoles
            ));
            break;
        }

        persistCachedGroups(groups);
        groupsLiveData.postValue(groups);
    }

    private void updateMemberRoleLocally(String groupServerId,
                                         int memberLocalId,
                                         String role) {
        List<Group> groups = getCachedGroups();
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group == null
                    || !groupServerId.equals(group.getServerId())) {
                continue;
            }

            Map<Integer, String> memberRoles = group.getMemberRoles() != null
                    ? new HashMap<>(group.getMemberRoles())
                    : new HashMap<>();
            memberRoles.put(memberLocalId, normalizeRole(role));

            groups.set(i, new Group(
                    stableId(group.getServerId()),
                    group.getServerId(),
                    group.getName(),
                    group.getAvatarLetter(),
                    group.getAvatarColor(),
                    group.getMembers(),
                    group.isOwner(),
                    memberRoles
            ));
            break;
        }

        persistCachedGroups(groups);
        groupsLiveData.postValue(groups);
    }

    private void replaceLocalGroupId(String oldGroupId, String newGroupId) {
        if (isBlank(oldGroupId) || isBlank(newGroupId)) {
            return;
        }

        List<Group> groups = getCachedGroups();
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group == null || !oldGroupId.equals(group.getServerId())) {
                continue;
            }

            groups.set(i, new Group(
                    stableId(newGroupId),
                    newGroupId,
                    group.getName(),
                    group.getAvatarLetter(),
                    group.getAvatarColor(),
                    group.getMembers(),
                    group.isOwner(),
                    group.getMemberRoles() != null
                            ? new HashMap<>(group.getMemberRoles())
                            : null
            ));
        }

        persistCachedGroups(groups);
        groupsLiveData.postValue(groups);
        rewriteQueuedGroupIds(oldGroupId, newGroupId);
    }

    private void rewriteQueuedGroupIds(String oldGroupId, String newGroupId) {
        if (socialActionQueueDao == null) {
            return;
        }

        List<SocialActionQueueEntity> pending = socialActionQueueDao
                .getPendingByScope(QUEUE_SCOPE);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        for (SocialActionQueueEntity entry : pending) {
            if (ACTION_CREATE_GROUP.equals(entry.actionType)) {
                CreateGroupPayload payload = gson.fromJson(entry.payload,
                        CreateGroupPayload.class);
                if (payload != null && oldGroupId.equals(payload.tempGroupId)) {
                    payload.tempGroupId = newGroupId;
                    entry.payload = gson.toJson(payload);
                    socialActionQueueDao.update(entry);
                }
                continue;
            }

            if (ACTION_UPDATE_GROUP.equals(entry.actionType)) {
                UpdateGroupPayload payload = gson.fromJson(entry.payload,
                        UpdateGroupPayload.class);
                if (payload != null && oldGroupId.equals(payload.groupId)) {
                    payload.groupId = newGroupId;
                    entry.payload = gson.toJson(payload);
                    socialActionQueueDao.update(entry);
                }
                continue;
            }

            if (ACTION_DELETE_GROUP.equals(entry.actionType)) {
                DeleteGroupPayload payload = gson.fromJson(entry.payload,
                        DeleteGroupPayload.class);
                if (payload != null && oldGroupId.equals(payload.groupId)) {
                    payload.groupId = newGroupId;
                    entry.payload = gson.toJson(payload);
                    socialActionQueueDao.update(entry);
                }
                continue;
            }

            if (ACTION_ADD_MEMBER.equals(entry.actionType)
                    || ACTION_REMOVE_MEMBER.equals(entry.actionType)
                    || ACTION_UPDATE_MEMBER_ROLE.equals(entry.actionType)) {
                GroupMemberPayload payload = gson.fromJson(entry.payload,
                        GroupMemberPayload.class);
                if (payload != null && oldGroupId.equals(payload.groupId)) {
                    payload.groupId = newGroupId;
                    entry.payload = gson.toJson(payload);
                    socialActionQueueDao.update(entry);
                }
            }
        }
    }

    private List<Group> getCachedGroups() {
        if (cachePrefs == null) {
            return groupsLiveData.getValue() != null
                    ? new ArrayList<>(groupsLiveData.getValue())
                    : new ArrayList<>();
        }

        String raw = cachePrefs.getString(CACHE_KEY_GROUPS, null);
        if (isBlank(raw)) {
            return new ArrayList<>();
        }

        try {
            Type type = new TypeToken<List<Group>>() { }
                    .getType();
            List<Group> groups = gson.fromJson(raw, type);
            return groups != null ? groups : new ArrayList<>();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private void persistCachedGroups(List<Group> groups) {
        if (cachePrefs != null) {
            cachePrefs.edit()
                    .putString(CACHE_KEY_GROUPS, gson.toJson(groups))
                    .apply();
        }
    }

    private void loadCachedGroupsIntoLiveData() {
        groupsLiveData.postValue(getCachedGroups());
    }

    private String resolveMemberServerId(int friendId) {
        String mappedId = userServerIdByLocalId.get(friendId);
        if (!isBlank(mappedId)) {
            return mappedId;
        }

        if (friendDao != null) {
            FriendEntity localFriend = friendDao.getByIdSync(friendId);
            if (localFriend != null && !isBlank(localFriend.serverUserId)) {
                return localFriend.serverUserId;
            }
        }

        return null;
    }

    private Friend resolveMemberPreview(int friendLocalId,
                                        String memberServerId) {
        if (friendDao != null) {
            FriendEntity localFriend = friendDao.getByIdSync(friendLocalId);
            if (localFriend != null) {
                return new Friend(
                        localFriend.id,
                        localFriend.serverUserId,
                        localFriend.name,
                        localFriend.avatarLetter,
                        localFriend.avatarColor,
                        localFriend.isOnline
                );
            }
        }

        UserApiModel user = usersById.get(memberServerId);
        if (user != null) {
            return new Friend(
                    stableId(user.id),
                    user.id,
                    user.name != null ? user.name : user.id,
                    !isBlank(user.avatarLetter)
                            ? user.avatarLetter
                            : safeAvatarLetter(
                            user.name != null ? user.name : user.id),
                    user.avatarColor,
                    user.isOnline
            );
        }

        return new Friend(
                friendLocalId,
                memberServerId,
                memberServerId,
                safeAvatarLetter(memberServerId),
                0xFF03DAC5,
                false
        );
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

    private String resolveActorId() {
        if (!isBlank(cachedActorId)) {
            return cachedActorId;
        }

        if (!isOnline()) {
            return null;
        }

        try {
            Response<AuthStateResponse> response = restApiService
                    .getAuthState().execute();
            if (!response.isSuccessful() || response.body() == null
                    || !response.body().authenticated
                    || isBlank(response.body().userId)) {
                return null;
            }
            cachedActorId = response.body().userId;
            return cachedActorId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean sameGroup(Group left, Group right) {
        if (left == null || right == null) {
            return false;
        }
        if (!isBlank(left.getServerId()) && !isBlank(right.getServerId())) {
            return left.getServerId().equals(right.getServerId());
        }
        return left.getId() == right.getId();
    }

    private boolean isOnline() {
        return networkMonitor != null && networkMonitor.isOnline();
    }

    private boolean isLocalPlaceholder(String groupId) {
        return !isBlank(groupId) && groupId.startsWith("local-");
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

    private enum QueueOutcome {
        SUCCESS,
        FAILED,
        DEFERRED
    }

    private static class CreateGroupPayload {
        public String tempGroupId;
        public String name;
        public int avatarColor;
        public List<String> memberIds;

        CreateGroupPayload(String tempGroupId, String name,
                           int avatarColor, List<String> memberIds) {
            this.tempGroupId = tempGroupId;
            this.name = name;
            this.avatarColor = avatarColor;
            this.memberIds = memberIds;
        }
    }

    private static class UpdateGroupPayload {
        public String groupId;
        public String name;
        public int avatarColor;

        UpdateGroupPayload(String groupId, String name, int avatarColor) {
            this.groupId = groupId;
            this.name = name;
            this.avatarColor = avatarColor;
        }
    }

    private static class GroupMemberPayload {
        public String groupId;
        public String memberId;
        public String role;

        GroupMemberPayload(String groupId, String memberId, String role) {
            this.groupId = groupId;
            this.memberId = memberId;
            this.role = role;
        }
    }

    private static class DeleteGroupPayload {
        public String groupId;

        DeleteGroupPayload(String groupId) {
            this.groupId = groupId;
        }
    }
}
