package com.bif.app.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.UserApiModel;
import com.bif.app.core.network.dto.friendship.CreateFriendRequestDto;
import com.bif.app.core.network.dto.friendship.FriendshipApiModel;
import com.bif.app.core.network.dto.friendship.UpdateFriendRequestStatusDto;
import com.bif.app.data.mapper.FriendMapper;
import com.bif.app.data.mapper.FriendshipMapper;
import com.bif.app.data.source.local.FriendDao;
import com.bif.app.data.source.local.FriendshipDao;
import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.repository.IFriendshipRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final MutableLiveData<List<Friend>> friendsLiveData;

    @Inject
    public FriendshipRepository(RestApiService restApiService,
                                FriendshipDao friendshipDao,
                                FriendDao friendDao) {
        this.restApiService = restApiService;
        this.friendshipDao = friendshipDao;
        this.friendDao = friendDao;
        this.executorService = Executors.newSingleThreadExecutor();
        this.pendingRequestsLiveData = new MutableLiveData<>(new ArrayList<>());
        this.friendsLiveData = new MutableLiveData<>(new ArrayList<>());
    }

    @Override
    public LiveData<List<Friendship>> getPendingRequests() {
        refreshPendingRequests();
        return pendingRequestsLiveData;
    }

    @Override
    public LiveData<List<Friend>> getFriends() {
        refreshFriends();
        return friendsLiveData;
    }

    @Override
    public void sendFriendRequest(String receiverId) {
        if (isBlank(receiverId)) {
            return;
        }
        executorService.execute(() -> {
            CreateFriendRequestDto request = new CreateFriendRequestDto();
            request.receiverId = receiverId.trim();
            try {
                restApiService.sendFriendRequest(request).execute();
            } catch (Exception ignored) {
                return;
            }
            refreshPendingRequestsSync();
        });
    }

    @Override
    public void acceptFriendRequest(int friendshipId) {
        updateFriendRequestStatus(friendshipId, "ACCEPTED", true);
    }

    @Override
    public void rejectFriendRequest(int friendshipId) {
        updateFriendRequestStatus(friendshipId, "REJECTED", false);
    }

    @Override
    public void refreshPendingRequests() {
        executorService.execute(this::refreshPendingRequestsSync);
    }

    @Override
    public void refreshFriends() {
        executorService.execute(this::refreshFriendsSync);
    }

    private void refreshPendingRequestsSync() {
        try {
            Response<List<FriendshipApiModel>> response = restApiService
                    .getPendingFriendRequests()
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                loadPendingFromLocalCache();
                return;
            }

            friendshipDao.replaceAll(FriendshipMapper.fromApiList(response.body()));
            loadPendingFromLocalCache();
        } catch (Exception ignored) {
            loadPendingFromLocalCache();
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

    private void loadPendingFromLocalCache() {
        List<Friendship> pending = FriendshipMapper.toDomainList(
                friendshipDao.getByStatus(com.bif.app.data.source.local.entity.FriendshipStatus.PENDING)
        );
        pendingRequestsLiveData.postValue(pending);
    }

    private void loadFriendsFromLocalCache() {
        List<FriendEntity> entities = friendDao.getAllFriendsSync();
        if (entities == null) {
            friendsLiveData.postValue(Collections.emptyList());
            return;
        }
        friendsLiveData.postValue(FriendMapper.toDomainList(entities));
    }

    private void updateFriendRequestStatus(int friendshipId,
                                           String status,
                                           boolean shouldRefreshFriends) {
        if (friendshipId <= 0) {
            return;
        }

        executorService.execute(() -> {
            UpdateFriendRequestStatusDto request = new UpdateFriendRequestStatusDto();
            request.status = status;

            try {
                restApiService.updateFriendRequestStatus(
                        String.valueOf(friendshipId),
                        request
                ).execute();
            } catch (Exception ignored) {
                return;
            }

            refreshPendingRequestsSync();
            if (shouldRefreshFriends) {
                refreshFriendsSync();
            }
        });
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