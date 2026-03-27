package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.UserApiModel;
import com.bif.app.core.network.dto.friendship.CreateFriendRequestDto;
import com.bif.app.core.network.dto.friendship.FriendshipApiModel;
import com.bif.app.data.mapper.FriendMapper;
import com.bif.app.data.mapper.FriendshipMapper;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.FriendshipDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.data.source.local.entity.FriendshipStatus;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.repository.IFriendshipRepository;

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

import retrofit2.Response;

@Singleton
public class FriendshipRepository implements IFriendshipRepository {
    private final RestApiService restApiService;
    private final FriendshipDao friendshipDao;
    private final FriendDao friendDao;
    private final ExecutorService executorService;
    private final MutableLiveData<List<Friendship>> pendingRequestsLiveData;
    private final MutableLiveData<List<Friendship>> outgoingRequestsLiveData;
    private final MutableLiveData<List<Friend>> friendsLiveData;
    private final Object requestLock = new Object();
    private final Set<String> inFlightRequestKeys = new HashSet<>();

    @Inject
    public FriendshipRepository(RestApiService restApiService,
                                FriendshipDao friendshipDao,
                                FriendDao friendDao) {
        this.restApiService = restApiService;
        this.friendshipDao = friendshipDao;
        this.friendDao = friendDao;
        this.executorService = Executors.newSingleThreadExecutor();
        this.pendingRequestsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.outgoingRequestsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.friendsLiveData = new MutableLiveData<>(new ArrayList<>());
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
        try {
            Response<List<UserApiModel>> response = restApiService.getUsers().execute();
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            for (UserApiModel user : response.body()) {
                if (user == null || isBlank(user.id)) {
                    continue;
                }

                String userId = user.id.trim();
                String userName = isBlank(user.name) ? "" : user.name.trim();
                if (userId.toLowerCase(Locale.ROOT).equals(normalizedQuery)
                        || userName.toLowerCase(Locale.ROOT).equals(normalizedQuery)) {
                    return userId;
                }
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void sendFriendRequest(String receiverId) {
        if (isBlank(receiverId)) {
            return;
        }
        String normalizedReceiverId = receiverId.trim();
        String currentUserId = fetchCurrentUserId();
        if (isBlank(currentUserId)) {
            throw new IllegalStateException("AUTH_USER_UNKNOWN");
        }
        if (currentUserId.equalsIgnoreCase(normalizedReceiverId)) {
            throw new IllegalStateException("SELF_REQUEST");
        }

        String requestKey = buildRequestKey(currentUserId, normalizedReceiverId);
        synchronized (requestLock) {
            if (inFlightRequestKeys.contains(requestKey)) {
                throw new IllegalStateException("REQUEST_PENDING");
            }
            inFlightRequestKeys.add(requestKey);
        }

        boolean reserved = false;
        long now = System.currentTimeMillis();
        try {
            // Reserve a local pending row to block duplicate taps while API request is in-flight.
            reserved = friendshipDao.reservePendingIfAbsent(currentUserId.trim(), normalizedReceiverId, now);
            if (!reserved) {
                FriendshipEntity existing = friendshipDao.findBetweenUsers(currentUserId, normalizedReceiverId);
                if (existing != null && existing.status == FriendshipStatus.PENDING) {
                    throw new IllegalStateException("REQUEST_PENDING");
                }
                if (existing != null && existing.status == FriendshipStatus.ACCEPTED) {
                    throw new IllegalStateException("ALREADY_FRIENDS");
                }
                // Existing REJECTED/CANCELED is allowed to resend.
            }

            refreshFriendsSync();
            refreshRequestCachesSync();

            if (hasAcceptedFriend(normalizedReceiverId)) {
                throw new IllegalStateException("ALREADY_FRIENDS");
            }

            FriendshipEntity existing = friendshipDao.findBetweenUsers(currentUserId, normalizedReceiverId);
            if (existing != null && existing.status == FriendshipStatus.PENDING
                    && !currentUserId.trim().equalsIgnoreCase(existing.requesterId)) {
                throw new IllegalStateException("REQUEST_PENDING");
            }

            CreateFriendRequestDto request = new CreateFriendRequestDto();
            request.receiverId = normalizedReceiverId;
            Response<FriendshipApiModel> response = restApiService.sendFriendRequest(request).execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("SEND_FAILED");
            }
        } catch (IllegalStateException illegalState) {
            if (reserved) {
                friendshipDao.rollbackReservedPending(currentUserId.trim(), normalizedReceiverId);
            }
            throw illegalState;
        } catch (Exception ignored) {
            if (reserved) {
                friendshipDao.rollbackReservedPending(currentUserId.trim(), normalizedReceiverId);
            }
            throw new IllegalStateException("SEND_FAILED");
        } finally {
            synchronized (requestLock) {
                inFlightRequestKeys.remove(requestKey);
            }
        }

        // Server creates a new PENDING record; refresh local cache after successful request.
        refreshRequestCachesSync();
    }

    @Override
    public void unfriend(String friendId) {
        if (isBlank(friendId)) {
            return;
        }

        try {
            Response<Void> response = restApiService.unfriend(friendId.trim()).execute();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("UNFRIEND_FAILED");
            }
        } catch (IllegalStateException stateException) {
            throw stateException;
        } catch (Exception ignored) {
            throw new IllegalStateException("UNFRIEND_FAILED");
        }

        refreshFriendsSync();
        refreshRequestCachesSync();
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
        executorService.execute(this::refreshRequestCachesSync);
    }

    @Override
    public void refreshOutgoingRequests() {
        executorService.execute(this::refreshRequestCachesSync);
    }

    @Override
    public void refreshFriends() {
        executorService.execute(this::refreshFriendsSync);
    }

    private void refreshRequestCachesSync() {
        try {
            Response<List<FriendshipApiModel>> incomingResponse = restApiService
                    .getIncomingFriendRequests()
                    .execute();
            Response<List<FriendshipApiModel>> outgoingResponse = restApiService
                    .getOutgoingFriendRequests()
                    .execute();

            if (!incomingResponse.isSuccessful() || incomingResponse.body() == null
                    || !outgoingResponse.isSuccessful() || outgoingResponse.body() == null) {
                loadRequestCachesFromLocal();
                return;
            }

            List<FriendshipEntity> incoming = FriendshipMapper.fromApiList(incomingResponse.body());
            List<FriendshipEntity> outgoing = FriendshipMapper.fromApiList(outgoingResponse.body());
            List<FriendshipEntity> combined = new ArrayList<>(incoming.size() + outgoing.size());
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

    private void refreshFriendsSync() {
        try {
            Response<List<UserApiModel>> response = restApiService.getFriends().execute();
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

    private void loadRequestCachesFromLocal() {
        String currentUserId = fetchCurrentUserId();
        if (isBlank(currentUserId)) {
            pendingRequestsLiveData.postValue(Collections.emptyList());
            outgoingRequestsLiveData.postValue(Collections.emptyList());
            return;
        }

        List<FriendshipEntity> pendingByUser = friendshipDao.getByUserIdAndStatus(
                currentUserId.trim(),
                com.bif.app.data.source.local.entity.FriendshipStatus.PENDING
        );

        List<FriendshipEntity> incoming = new ArrayList<>();
        List<FriendshipEntity> outgoing = new ArrayList<>();
        for (FriendshipEntity item : pendingByUser) {
            if (item == null) {
                continue;
            }

            if (!isBlank(item.receiverId)
                    && currentUserId.trim().equalsIgnoreCase(item.receiverId.trim())) {
                incoming.add(item);
            } else if (!isBlank(item.requesterId)
                    && currentUserId.trim().equalsIgnoreCase(item.requesterId.trim())) {
                outgoing.add(item);
            }
        }

        pendingRequestsLiveData.postValue(FriendshipMapper.toDomainList(incoming));
        outgoingRequestsLiveData.postValue(FriendshipMapper.toDomainList(outgoing));
    }

    private void loadFriendsFromLocalCache() {
        List<FriendEntity> entities = friendDao.getAllFriendsSync();
        if (entities == null) {
            friendsLiveData.postValue(Collections.emptyList());
            return;
        }
        friendsLiveData.postValue(FriendMapper.toDomainList(entities));
    }

    private void handleFriendRequestDecision(int friendshipId, boolean accept) {
        if (friendshipId <= 0) {
            return;
        }

        FriendshipEntity existing = friendshipDao.getById(friendshipId);
        if (existing == null || existing.status != FriendshipStatus.PENDING) {
            throw new IllegalStateException("REQUEST_NOT_FOUND");
        }

        String currentUserId = fetchCurrentUserId();
        if (isBlank(currentUserId) || isBlank(existing.receiverId)
                || !currentUserId.trim().equalsIgnoreCase(existing.receiverId.trim())) {
            throw new IllegalStateException("NOT_REQUEST_RECEIVER");
        }

        try {
            Response<FriendshipApiModel> response;
            if (accept) {
                response = restApiService.acceptFriendRequest(existing.serverId != null ? existing.serverId : String.valueOf(friendshipId)).execute();
            } else {
                response = restApiService.rejectFriendRequest(existing.serverId != null ? existing.serverId : String.valueOf(friendshipId)).execute();
            }

            if (!response.isSuccessful()) {
                throw new IllegalStateException(accept ? "ACCEPT_FAILED" : "REJECT_FAILED");
            }
        } catch (IllegalStateException stateException) {
            throw stateException;
        } catch (Exception ignored) {
            throw new IllegalStateException(accept ? "ACCEPT_FAILED" : "REJECT_FAILED");
        }

        refreshRequestCachesSync();
        if (accept) {
            refreshFriendsSync();
        }
    }

    private boolean containsFriendshipId(List<FriendshipEntity> items, int id) {
        for (FriendshipEntity item : items) {
            if (item != null && item.id == id) {
                return true;
            }
        }
        return false;
    }

    private List<FriendEntity> mapUsersToFriendEntities(List<UserApiModel> users) {
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
            entity.name = !isBlank(user.name) ? user.name.trim() : user.id.trim();
            entity.avatarLetter = !isBlank(user.avatarLetter)
                    ? user.avatarLetter.trim()
                    : safeAvatarLetter(entity.name);
            entity.avatarColor = user.avatarColor;
            entity.isOnline = user.isOnline;
            entities.add(entity);
        }

        return entities;
    }

    private int stableId(String source) {
        return Math.abs(source.trim().hashCode());
    }

    private String fetchCurrentUserId() {
        try {
            Response<com.bif.app.core.network.dto.auth.AuthStateResponse> response = restApiService.getAuthState().execute();
            if (!response.isSuccessful() || response.body() == null || !response.body().authenticated) {
                return null;
            }
            return response.body().userId;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasAcceptedFriend(String targetUserId) {
        List<FriendEntity> currentFriends = friendDao.getAllFriendsSync();
        if (currentFriends == null || currentFriends.isEmpty()) {
            return false;
        }

        for (FriendEntity friend : currentFriends) {
            if (friend != null && !isBlank(friend.serverUserId)
                    && friend.serverUserId.trim().equalsIgnoreCase(targetUserId)) {
                return true;
            }
        }

        return false;
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
        return String.valueOf(value.trim().charAt(0)).toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}