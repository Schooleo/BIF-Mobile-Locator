package com.bif.app.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.UserApiModel;
import com.bif.app.core.network.dto.auth.AuthStateResponse;
import com.bif.app.core.network.dto.friendship.CreateFriendRequestDto;
import com.bif.app.core.network.dto.friendship.FriendshipApiModel;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.mapper.FriendMapper;
import com.bif.app.data.mapper.FriendshipMapper;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.FriendshipDao;
import com.bif.app.data.source.local.SocialActionQueueDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.FriendshipStatus;
import com.bif.app.data.source.local.entity.SocialActionQueueEntity;
import com.bif.app.data.sync.NetworkMonitor;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Response;

@Singleton
public class FriendshipRepository implements IFriendshipRepository {

    private static final String QUEUE_SCOPE = "FRIENDSHIP";
    private static final String ACTION_SEND_REQUEST = "SEND_REQUEST";
    private static final String ACTION_UNFRIEND = "UNFRIEND";
    private static final String ACTION_ACCEPT_REQUEST = "ACCEPT_REQUEST";
    private static final String ACTION_REJECT_REQUEST = "REJECT_REQUEST";
    private static final int MAX_RETRY_COUNT = 5;

    private final RestApiService restApiService;
    private final FriendshipDao friendshipDao;
    private final FriendDao friendDao;
    private final SocialActionQueueDao socialActionQueueDao;
    private final NetworkMonitor networkMonitor;
    private final Context appContext;
    private final ExecutorService executorService;
    private final MutableLiveData<List<Friendship>> pendingRequestsLiveData;
    private final MutableLiveData<List<Friendship>> outgoingRequestsLiveData;
    private final MutableLiveData<List<Friend>> friendsLiveData;
    private final Object requestLock = new Object();
    private final Set<String> inFlightRequestKeys = new HashSet<>();
    private final Gson gson;

    private volatile String cachedUserId;

    @Inject
    public FriendshipRepository(RestApiService restApiService,
                                FriendshipDao friendshipDao,
                                FriendDao friendDao,
                                SocialActionQueueDao socialActionQueueDao,
                                NetworkMonitor networkMonitor,
                                @ApplicationContext Context appContext) {
        this.restApiService = restApiService;
        this.friendshipDao = friendshipDao;
        this.friendDao = friendDao;
        this.socialActionQueueDao = socialActionQueueDao;
        this.networkMonitor = networkMonitor;
        this.appContext = appContext;
        this.executorService = Executors.newSingleThreadExecutor();
        this.pendingRequestsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.outgoingRequestsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.friendsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.gson = new Gson();

        if (this.networkMonitor != null) {
            this.networkMonitor.observeConnectivity().observeForever(
                    connected -> {
                        if (Boolean.TRUE.equals(connected)) {
                            executorService.execute(() -> {
                                replayQueuedActions();
                                refreshFriendsSync(false);
                                refreshRequestCachesSync(false);
                            });
                        }
                    }
            );
        }
    }

    // Backward-compatible constructor for unit tests.
    public FriendshipRepository(RestApiService restApiService,
                                FriendshipDao friendshipDao,
                                FriendDao friendDao) {
        this(restApiService, friendshipDao, friendDao,
                null, null, null);
    }

    @Override
    public LiveData<List<Friendship>> getPendingRequests() {
        refreshPendingRequests();
        return pendingRequestsLiveData;
    }

    @Override
    public LiveData<List<Friendship>> getOutgoingRequests() {
        refreshOutgoingRequests();
        return outgoingRequestsLiveData;
    }

    @Override
    public LiveData<List<Friend>> getFriends() {
        refreshFriends();
        return friendsLiveData;
    }

    @Override
    public String resolveUserId(String query) {
        if (isBlank(query)) {
            return null;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (isOnline()) {
            try {
                Response<List<UserApiModel>> response = restApiService.getUsers()
                        .execute();
                if (response.isSuccessful() && response.body() != null) {
                    for (UserApiModel user : response.body()) {
                        if (user == null || isBlank(user.id)) {
                            continue;
                        }

                        String userId = user.id.trim();
                        String userName = isBlank(user.name)
                                ? "" : user.name.trim();
                        if (userId.toLowerCase(Locale.ROOT)
                                .equals(normalizedQuery)
                                || userName.toLowerCase(Locale.ROOT)
                                .equals(normalizedQuery)) {
                            return userId;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fall through to local cache lookup.
            }
        }

        List<FriendEntity> localFriends = friendDao.getAllFriendsSync();
        if (localFriends != null) {
            for (FriendEntity friend : localFriends) {
                if (friend == null || isBlank(friend.serverUserId)) {
                    continue;
                }
                if (friend.serverUserId.trim().toLowerCase(Locale.ROOT)
                        .equals(normalizedQuery)
                        || (!isBlank(friend.name)
                        && friend.name.trim().toLowerCase(Locale.ROOT)
                        .equals(normalizedQuery))) {
                    return friend.serverUserId.trim();
                }
            }
        }

        return query.trim();
    }

    @Override
    public void sendFriendRequest(String receiverId) {
        if (isBlank(receiverId)) {
            return;
        }

        String receiverLookup = receiverId.trim();
        boolean online = isOnline();
        String receiverIdForApi = receiverLookup;
        if (online) {
            String resolvedReceiverId = resolveReceiverIdStrict(receiverLookup);
            if (isBlank(resolvedReceiverId)) {
                throw new IllegalStateException("USER_NOT_FOUND");
            }
            receiverIdForApi = resolvedReceiverId;
        }

        String localReceiverValue = online ? receiverIdForApi : receiverLookup;
        String currentUserId = resolveCurrentUserId();
        if (isBlank(currentUserId)) {
            throw new IllegalStateException("AUTH_USER_UNKNOWN");
        }
        if (currentUserId.equalsIgnoreCase(receiverIdForApi)) {
            throw new IllegalStateException("SELF_REQUEST");
        }

        String requestKey = buildRequestKey(currentUserId,
                localReceiverValue);
        synchronized (requestLock) {
            if (inFlightRequestKeys.contains(requestKey)) {
                throw new IllegalStateException("REQUEST_PENDING");
            }
            inFlightRequestKeys.add(requestKey);
        }

        boolean reserved = false;
        long now = System.currentTimeMillis();
        try {
            reserved = friendshipDao.reservePendingIfAbsent(
                    currentUserId.trim(), localReceiverValue, now);
            if (!reserved) {
                FriendshipEntity existing = friendshipDao.findBetweenUsers(
                        currentUserId, localReceiverValue);
                if (existing != null
                        && existing.status == FriendshipStatus.PENDING) {
                    throw new IllegalStateException("REQUEST_PENDING");
                }
                if (existing != null
                        && existing.status == FriendshipStatus.ACCEPTED) {
                    throw new IllegalStateException("ALREADY_FRIENDS");
                }
            }

            if (hasAcceptedFriend(receiverIdForApi)) {
                throw new IllegalStateException("ALREADY_FRIENDS");
            }

            FriendshipEntity existing = friendshipDao.findBetweenUsers(
                    currentUserId, localReceiverValue);
            if (existing != null
                    && existing.status == FriendshipStatus.PENDING
                    && !currentUserId.trim().equalsIgnoreCase(
                    existing.requesterId)) {
                throw new IllegalStateException("REQUEST_PENDING");
            }

            if (online) {
                CreateFriendRequestDto request = new CreateFriendRequestDto();
                request.receiverId = receiverIdForApi;
                Response<FriendshipApiModel> response = restApiService
                        .sendFriendRequest(request).execute();
                if (!response.isSuccessful()) {
                    if (reserved) {
                        friendshipDao.rollbackReservedPending(
                                currentUserId.trim(),
                                localReceiverValue);
                    }
                    throw new IllegalStateException("SEND_FAILED");
                }

                if (!receiverLookup.equalsIgnoreCase(receiverIdForApi)) {
                    friendshipDao.remapPendingReceiver(currentUserId,
                            receiverLookup, receiverIdForApi,
                            System.currentTimeMillis());
                }

                refreshRequestCachesSync(false);
                return;
            }

            enqueueAction(ACTION_SEND_REQUEST,
                    new FriendRequestQueuePayload(receiverLookup));
            loadRequestCachesFromLocal();
        } catch (IllegalStateException illegalState) {
            if (reserved
                    && ("REQUEST_PENDING".equals(illegalState.getMessage())
                    || "ALREADY_FRIENDS".equals(illegalState.getMessage())
                    || "SELF_REQUEST".equals(illegalState.getMessage())
                    || "SEND_FAILED".equals(illegalState.getMessage()))) {
                friendshipDao.rollbackReservedPending(
                        currentUserId.trim(), localReceiverValue);
            }
            throw illegalState;
        } catch (Exception ignored) {
            enqueueAction(ACTION_SEND_REQUEST,
                    new FriendRequestQueuePayload(receiverLookup));
            loadRequestCachesFromLocal();
        } finally {
            synchronized (requestLock) {
                inFlightRequestKeys.remove(requestKey);
            }
        }
    }

    @Override
    public void unfriend(String friendId) {
        if (isBlank(friendId)) {
            return;
        }

        String normalizedFriendId = friendId.trim();
        String currentUserId = resolveCurrentUserId();

        applyLocalUnfriend(currentUserId, normalizedFriendId);

        if (isOnline()) {
            try {
                Response<Void> response = restApiService.unfriend(
                        normalizedFriendId).execute();
                if (response.isSuccessful()) {
                    refreshFriendsSync(false);
                    refreshRequestCachesSync(false);
                    return;
                }
            } catch (Exception ignored) {
                // Queue below.
            }
        }

        enqueueAction(ACTION_UNFRIEND,
                new FriendIdQueuePayload(normalizedFriendId));
        loadFriendsFromLocalCache();
        loadRequestCachesFromLocal();
    }

    @Override
    public void acceptFriendRequest(int friendshipId) {
        handleFriendRequestDecision(friendshipId, true);
    }

    @Override
    public void rejectFriendRequest(int friendshipId) {
        handleFriendRequestDecision(friendshipId, false);
    }

    @Override
    public void refreshPendingRequests() {
        executorService.execute(() -> refreshRequestCachesSync(true));
    }

    @Override
    public void refreshOutgoingRequests() {
        executorService.execute(() -> refreshRequestCachesSync(true));
    }

    @Override
    public void refreshFriends() {
        executorService.execute(() -> refreshFriendsSync(true));
    }

    @Override
    public void clearCache() {
        executorService.execute(() -> {
            cachedUserId = null;
            synchronized (requestLock) {
                inFlightRequestKeys.clear();
            }
            pendingRequestsLiveData.postValue(Collections.emptyList());
            outgoingRequestsLiveData.postValue(Collections.emptyList());
            friendsLiveData.postValue(Collections.emptyList());
        });
    }

    private void refreshRequestCachesSync(boolean replayQueue) {
        if (replayQueue) {
            replayQueuedActions();
        }

        if (!isOnline()) {
            loadRequestCachesFromLocal();
            return;
        }

        try {
            Response<List<FriendshipApiModel>> incomingResponse =
                    restApiService.getIncomingFriendRequests().execute();
            Response<List<FriendshipApiModel>> outgoingResponse =
                    restApiService.getOutgoingFriendRequests().execute();

            if (!incomingResponse.isSuccessful()
                    || incomingResponse.body() == null
                    || !outgoingResponse.isSuccessful()
                    || outgoingResponse.body() == null) {
                loadRequestCachesFromLocal();
                return;
            }

            List<FriendshipEntity> incoming = FriendshipMapper.fromApiList(
                    incomingResponse.body());
            List<FriendshipEntity> outgoing = FriendshipMapper.fromApiList(
                    outgoingResponse.body());

            List<FriendshipEntity> combined = new ArrayList<>(
                    incoming.size() + outgoing.size());
            combined.addAll(incoming);
            for (FriendshipEntity candidate : outgoing) {
                if (!containsFriendshipId(combined, candidate.id)) {
                    combined.add(candidate);
                }
            }

            friendshipDao.replaceAll(combined);
            loadRequestCachesFromLocal();
        } catch (Exception ignored) {
            loadRequestCachesFromLocal();
        }
    }

    private void refreshFriendsSync(boolean replayQueue) {
        if (replayQueue) {
            replayQueuedActions();
        }

        if (!isOnline()) {
            loadFriendsFromLocalCache();
            return;
        }

        try {
            Response<List<UserApiModel>> response = restApiService
                    .getFriends().execute();
            if (!response.isSuccessful() || response.body() == null) {
                loadFriendsFromLocalCache();
                return;
            }

            friendDao.replaceAll(mapUsersToFriendEntities(response.body()));
            loadFriendsFromLocalCache();
        } catch (Exception ignored) {
            loadFriendsFromLocalCache();
        }
    }

    private void handleFriendRequestDecision(int friendshipId,
                                             boolean accept) {
        if (friendshipId <= 0) {
            return;
        }

        FriendshipEntity existing = friendshipDao.getById(friendshipId);
        if (existing == null || existing.status != FriendshipStatus.PENDING) {
            throw new IllegalStateException("REQUEST_NOT_FOUND");
        }

        String currentUserId = resolveCurrentUserId();
        if (isBlank(currentUserId) || isBlank(existing.receiverId)
                || !currentUserId.trim().equalsIgnoreCase(
                existing.receiverId.trim())) {
            throw new IllegalStateException("NOT_REQUEST_RECEIVER");
        }

        applyLocalFriendRequestDecision(existing, accept);

        String targetRequestId = !isBlank(existing.serverId)
                ? existing.serverId
                : String.valueOf(friendshipId);

        if (isOnline()) {
            try {
                Response<FriendshipApiModel> response;
                if (accept) {
                    response = restApiService.acceptFriendRequest(
                            targetRequestId).execute();
                } else {
                    response = restApiService.rejectFriendRequest(
                            targetRequestId).execute();
                }
                if (response.isSuccessful()) {
                    refreshRequestCachesSync(false);
                    if (accept) {
                        refreshFriendsSync(false);
                    }
                    return;
                }
            } catch (Exception ignored) {
                // Queue below.
            }
        }

        enqueueAction(accept ? ACTION_ACCEPT_REQUEST : ACTION_REJECT_REQUEST,
                new RequestDecisionQueuePayload(targetRequestId));
        loadRequestCachesFromLocal();
        if (accept) {
            loadFriendsFromLocalCache();
        }
    }

    private void applyLocalFriendRequestDecision(FriendshipEntity existing,
                                                 boolean accept) {
        if (existing == null) {
            return;
        }

        existing.status = accept ? FriendshipStatus.ACCEPTED
                : FriendshipStatus.REJECTED;
        existing.updatedAt = System.currentTimeMillis();
        friendshipDao.update(existing);

        if (!accept) {
            return;
        }

        if (isBlank(existing.requesterId)) {
            return;
        }

        FriendEntity friendEntity = friendDao.getByServerUserIdSync(
                existing.requesterId);
        if (friendEntity == null) {
            friendEntity = new FriendEntity();
            friendEntity.id = stableId(existing.requesterId);
            friendEntity.serverUserId = existing.requesterId;
        }
        friendEntity.name = !isBlank(existing.requesterName)
                ? existing.requesterName
                : existing.requesterId;
        friendEntity.avatarLetter = safeAvatarLetter(friendEntity.name);
        friendEntity.avatarColor = 0xFF03DAC5;
        friendEntity.isOnline = false;
        friendDao.insert(friendEntity);
    }

    private void applyLocalUnfriend(String currentUserId, String friendId) {
        friendDao.deleteByServerUserId(friendId);
        if (!isBlank(currentUserId)) {
            friendshipDao.deleteBetweenUsers(currentUserId, friendId);
        }
    }

    private void replayQueuedActions() {
        if (socialActionQueueDao == null || !isOnline()) {
            return;
        }

        String currentUserId = resolveCurrentUserId();
        if (isBlank(currentUserId)) {
            return;
        }

        socialActionQueueDao.resetInFlight(currentUserId);
        List<SocialActionQueueEntity> pending = socialActionQueueDao
                .getPendingByScope(QUEUE_SCOPE, currentUserId);
        if (pending == null || pending.isEmpty()) {
            return;
        }

        boolean hasApplied = false;
        for (SocialActionQueueEntity entry : pending) {
            entry.status = "IN_FLIGHT";
            socialActionQueueDao.update(entry);

            boolean success = executeQueuedAction(entry);
            if (success) {
                socialActionQueueDao.remove(entry.id);
                hasApplied = true;
            } else {
                entry.retryCount++;
                entry.status = entry.retryCount > MAX_RETRY_COUNT
                        ? "FAILED" : "PENDING";
                socialActionQueueDao.update(entry);
            }
        }

        if (hasApplied) {
            refreshFriendsSync(false);
            refreshRequestCachesSync(false);
        }
    }

    private boolean executeQueuedAction(SocialActionQueueEntity entry) {
        try {
            if (ACTION_SEND_REQUEST.equals(entry.actionType)) {
                FriendRequestQueuePayload payload = gson.fromJson(
                        entry.payload, FriendRequestQueuePayload.class);
                String receiverLookup = extractReceiverLookup(payload);
                if (payload == null || isBlank(receiverLookup)) {
                    return false;
                }

                String currentUserId = resolveCurrentUserId();
                String resolvedReceiverId = resolveReceiverIdStrict(
                        receiverLookup);
                if (isBlank(resolvedReceiverId)) {
                    return false;
                }

                if (!isBlank(currentUserId)
                        && !receiverLookup.equalsIgnoreCase(
                        resolvedReceiverId)) {
                    friendshipDao.remapPendingReceiver(currentUserId,
                            receiverLookup, resolvedReceiverId,
                            System.currentTimeMillis());
                }

                payload.receiverUsername = receiverLookup;
                payload.receiverId = resolvedReceiverId;
                entry.payload = gson.toJson(payload);
                socialActionQueueDao.update(entry);

                CreateFriendRequestDto request = new CreateFriendRequestDto();
                request.receiverId = resolvedReceiverId;
                Response<FriendshipApiModel> response = restApiService
                        .sendFriendRequest(request).execute();
                return response.isSuccessful();
            }

            if (ACTION_UNFRIEND.equals(entry.actionType)) {
                FriendIdQueuePayload payload = gson.fromJson(
                        entry.payload, FriendIdQueuePayload.class);
                if (payload == null || isBlank(payload.friendId)) {
                    return false;
                }
                Response<Void> response = restApiService
                        .unfriend(payload.friendId).execute();
                return response.isSuccessful();
            }

            if (ACTION_ACCEPT_REQUEST.equals(entry.actionType)) {
                RequestDecisionQueuePayload payload = gson.fromJson(
                        entry.payload, RequestDecisionQueuePayload.class);
                if (payload == null || isBlank(payload.requestId)) {
                    return false;
                }
                Response<FriendshipApiModel> response = restApiService
                        .acceptFriendRequest(payload.requestId).execute();
                return response.isSuccessful();
            }

            if (ACTION_REJECT_REQUEST.equals(entry.actionType)) {
                RequestDecisionQueuePayload payload = gson.fromJson(
                        entry.payload, RequestDecisionQueuePayload.class);
                if (payload == null || isBlank(payload.requestId)) {
                    return false;
                }
                Response<FriendshipApiModel> response = restApiService
                        .rejectFriendRequest(payload.requestId).execute();
                return response.isSuccessful();
            }

            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void enqueueAction(String actionType, Object payload) {
        if (socialActionQueueDao == null) {
            return;
        }

        String currentUserId = resolveCurrentUserId();
        if (isBlank(currentUserId)) {
            return;
        }

        SocialActionQueueEntity entry = new SocialActionQueueEntity();
        entry.userId = currentUserId;
        entry.scope = QUEUE_SCOPE;
        entry.actionType = actionType;
        entry.payload = payload == null ? null : gson.toJson(payload);
        entry.status = "PENDING";
        entry.retryCount = 0;
        entry.createdAt = System.currentTimeMillis();
        socialActionQueueDao.enqueue(entry);
    }

    private void loadRequestCachesFromLocal() {
        String currentUserId = resolveCurrentUserId();
        if (isBlank(currentUserId)) {
            pendingRequestsLiveData.postValue(Collections.emptyList());
            outgoingRequestsLiveData.postValue(Collections.emptyList());
            return;
        }

        List<FriendshipEntity> pendingByUser = friendshipDao
                .getByUserIdAndStatus(currentUserId.trim(),
                        FriendshipStatus.PENDING);

        List<FriendshipEntity> incoming = new ArrayList<>();
        List<FriendshipEntity> outgoing = new ArrayList<>();
        for (FriendshipEntity item : pendingByUser) {
            if (item == null) {
                continue;
            }

            if (!isBlank(item.receiverId)
                    && currentUserId.trim().equalsIgnoreCase(
                    item.receiverId.trim())) {
                incoming.add(item);
            } else if (!isBlank(item.requesterId)
                    && currentUserId.trim().equalsIgnoreCase(
                    item.requesterId.trim())) {
                outgoing.add(item);
            }
        }

        pendingRequestsLiveData.postValue(FriendshipMapper.toDomainList(
                incoming));
        outgoingRequestsLiveData.postValue(FriendshipMapper.toDomainList(
                outgoing));
    }

    private void loadFriendsFromLocalCache() {
        List<FriendEntity> entities = friendDao.getAllFriendsSync();
        if (entities == null) {
            friendsLiveData.postValue(Collections.emptyList());
            return;
        }
        friendsLiveData.postValue(FriendMapper.toDomainList(entities));
    }

    private boolean containsFriendshipId(List<FriendshipEntity> items,
                                         int id) {
        for (FriendshipEntity item : items) {
            if (item != null && item.id == id) {
                return true;
            }
        }
        return false;
    }

    private List<FriendEntity> mapUsersToFriendEntities(
            List<UserApiModel> users) {
        List<FriendEntity> entities = new ArrayList<>();
        if (users == null) {
            return entities;
        }

        for (UserApiModel user : users) {
            if (user == null || isBlank(user.id)) {
                continue;
            }

            FriendEntity entity = new FriendEntity();
            entity.id = stableId(user.id);
            entity.serverUserId = user.id.trim();
            entity.name = !isBlank(user.name)
                    ? user.name.trim() : user.id.trim();
            entity.avatarLetter = !isBlank(user.avatarLetter)
                    ? user.avatarLetter.trim()
                    : safeAvatarLetter(entity.name);
            entity.avatarColor = user.avatarColor;
            entity.isOnline = user.isOnline;
            entities.add(entity);
        }

        return entities;
    }

    private String resolveCurrentUserId() {
        if (appContext != null) {
            String prefUserId = UserPreferences.getId(appContext);
            if (isBlank(prefUserId)) {
                prefUserId = UserPreferences.getUsername(appContext);
            }
            if (!isBlank(prefUserId)) {
                cachedUserId = prefUserId.trim();
                return cachedUserId;
            }
        }

        if (!isBlank(cachedUserId)) {
            return cachedUserId;
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
            cachedUserId = response.body().userId.trim();
            return cachedUserId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveReceiverIdStrict(String query) {
        if (isBlank(query)) {
            return null;
        }

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        if (isOnline()) {
            try {
                Response<List<UserApiModel>> response = restApiService.getUsers()
                        .execute();
                if (response.isSuccessful() && response.body() != null) {
                    for (UserApiModel user : response.body()) {
                        if (user == null || isBlank(user.id)) {
                            continue;
                        }

                        String userId = user.id.trim();
                        String userName = isBlank(user.name)
                                ? "" : user.name.trim();
                        if (userId.toLowerCase(Locale.ROOT)
                                .equals(normalizedQuery)
                                || userName.toLowerCase(Locale.ROOT)
                                .equals(normalizedQuery)) {
                            return userId;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fall through to local cache lookup.
            }
        }

        List<FriendEntity> localFriends = friendDao.getAllFriendsSync();
        if (localFriends != null) {
            for (FriendEntity friend : localFriends) {
                if (friend == null || isBlank(friend.serverUserId)) {
                    continue;
                }
                if (friend.serverUserId.trim().toLowerCase(Locale.ROOT)
                        .equals(normalizedQuery)
                        || (!isBlank(friend.name)
                        && friend.name.trim().toLowerCase(Locale.ROOT)
                        .equals(normalizedQuery))) {
                    return friend.serverUserId.trim();
                }
            }
        }

        return null;
    }

    private String extractReceiverLookup(FriendRequestQueuePayload payload) {
        if (payload == null) {
            return null;
        }

        if (!isBlank(payload.receiverUsername)) {
            return payload.receiverUsername.trim();
        }
        if (!isBlank(payload.receiverId)) {
            return payload.receiverId.trim();
        }
        return null;
    }

    private boolean hasAcceptedFriend(String targetUserId) {
        List<FriendEntity> currentFriends = friendDao.getAllFriendsSync();
        if (currentFriends == null || currentFriends.isEmpty()) {
            return false;
        }

        for (FriendEntity friend : currentFriends) {
            if (friend != null && !isBlank(friend.serverUserId)
                    && friend.serverUserId.trim().equalsIgnoreCase(
                    targetUserId)) {
                return true;
            }
        }
        return false;
    }

    private int stableId(String source) {
        return Math.abs(source.trim().hashCode());
    }

    private boolean isOnline() {
        return networkMonitor != null && networkMonitor.isOnline();
    }

    private String buildRequestKey(String userA, String userB) {
        String a = userA == null ? "" : userA.trim().toLowerCase(Locale.ROOT);
        String b = userB == null ? "" : userB.trim().toLowerCase(Locale.ROOT);
        return a + "->" + b;
    }

    private String safeAvatarLetter(String value) {
        if (isBlank(value)) {
            return "U";
        }
        return String.valueOf(value.trim().charAt(0)).toUpperCase(
                java.util.Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class FriendRequestQueuePayload {
        public String receiverId;
        public String receiverUsername;

        public FriendRequestQueuePayload() {
        }

        public FriendRequestQueuePayload(String receiverUsername) {
            this.receiverUsername = receiverUsername;
            this.receiverId = receiverUsername;
        }
    }

    public static class FriendIdQueuePayload {
        public String friendId;

        public FriendIdQueuePayload() {
        }

        public FriendIdQueuePayload(String friendId) {
            this.friendId = friendId;
        }
    }

    public static class RequestDecisionQueuePayload {
        public String requestId;

        public RequestDecisionQueuePayload() {
        }

        public RequestDecisionQueuePayload(String requestId) {
            this.requestId = requestId;
        }
    }
}
