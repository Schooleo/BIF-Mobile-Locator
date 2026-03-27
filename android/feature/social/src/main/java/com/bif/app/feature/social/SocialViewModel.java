package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.repository.IFriendshipRepository;
import com.bif.app.domain.model.Group;
import com.bif.app.domain.repository.IGroupRepository;
import com.bif.app.feature.social.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class SocialViewModel extends ViewModel {
    private final IFriendshipRepository friendshipRepository;
    private final IGroupRepository groupRepository;
    private final LiveData<List<Friend>> friends;
    private final LiveData<List<Friendship>> pendingRequests;
    private final LiveData<List<Group>> groups;
    private final MediatorLiveData<UiState<List<Friend>>> friendUiState = new MediatorLiveData<>();
    private final MediatorLiveData<UiState<List<Group>>> groupUiState = new MediatorLiveData<>();
    private final MutableLiveData<String> friendActionMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> friendActionLoading = new MutableLiveData<>(false);
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public SocialViewModel(IFriendshipRepository friendshipRepository,
                           IGroupRepository groupRepository) {
        this.friendshipRepository = friendshipRepository;
        this.groupRepository = groupRepository;
        this.friends = friendshipRepository.getFriends();
        this.pendingRequests = friendshipRepository.getPendingRequests();
        this.groups = groupRepository.getGroups();

        friendUiState.setValue(UiState.loading());
        groupUiState.setValue(UiState.loading());

        friendUiState.addSource(this.friends, this::mapFriendState);
        groupUiState.addSource(this.groups, this::mapGroupState);
    }

    public LiveData<List<Friend>> getFriends() {
        return friends;
    }

    public LiveData<List<Friendship>> getPendingRequests() {
        return pendingRequests;
    }

    public LiveData<List<Group>> getGroups() { return groups; }

    public LiveData<UiState<List<Friend>>> getFriendUiState() {
        return friendUiState;
    }

    public LiveData<UiState<List<Group>>> getGroupUiState() {
        return groupUiState;
    }

    public LiveData<String> getFriendActionMessage() {
        return friendActionMessage;
    }

    public LiveData<Boolean> getFriendActionLoading() {
        return friendActionLoading;
    }

    public void clearFriendActionMessage() {
        friendActionMessage.setValue(null);
    }

    public void retryFriends() {
        mapFriendState(friends.getValue());
        friendshipRepository.refreshFriends();
        friendshipRepository.refreshPendingRequests();
    }

    public void refreshRequestsOnly() {
        friendshipRepository.refreshPendingRequests();
    }

    public void retryGroups() {
        mapGroupState(groups.getValue());
    }

    public void addFriend(String receiverId) {
        runFriendAction(() -> {
            String resolvedUserId = friendshipRepository.resolveUserId(receiverId);
            if (resolvedUserId == null || resolvedUserId.trim().isEmpty()) {
                throw new IllegalStateException("USER_NOT_FOUND");
            }

            friendshipRepository.sendFriendRequest(resolvedUserId);
            friendshipRepository.refreshPendingRequests();
            return "__MSG_FRIEND_REQUEST_SENT__";
        }, "__MSG_FRIEND_REQUEST_SEND_FAILED__");
    }

    public void deleteFriend(Friend friend) {
        if (friend == null || friend.getServerUserId() == null || friend.getServerUserId().trim().isEmpty()) {
            friendUiState.setValue(UiState.error("Unable to unfriend: missing user id."));
            return;
        }

        runFriendAction(() -> {
            friendshipRepository.unfriend(friend.getServerUserId());
            friendshipRepository.refreshFriends();
            friendshipRepository.refreshPendingRequests();
            return "__MSG_UNFRIEND_SUCCESS__";
        }, "__MSG_UNFRIEND_FAILED__");
    }

    public void acceptFriendRequest(int friendshipId) {
        runFriendAction(() -> {
            friendshipRepository.acceptFriendRequest(friendshipId);
            friendshipRepository.refreshPendingRequests();
            friendshipRepository.refreshFriends();
            return "__MSG_FRIEND_REQUEST_ACCEPT_SUCCESS__";
        }, "__MSG_FRIEND_REQUEST_ACCEPT_FAILED__");
    }

    public void rejectFriendRequest(int friendshipId) {
        runFriendAction(() -> {
            friendshipRepository.rejectFriendRequest(friendshipId);
            friendshipRepository.refreshPendingRequests();
            return "__MSG_FRIEND_REQUEST_REJECT_SUCCESS__";
        }, "__MSG_FRIEND_REQUEST_REJECT_FAILED__");
    }

    public void createGroup(String groupName, List<Friend> selectedMembers) {
        try {
            groupRepository.createGroup(groupName, selectedMembers);
        } catch (RuntimeException exception) {
            groupUiState.setValue(UiState.error("Unable to create group."));
        }
    }

    public void handleGroupAction(Group group) {
        try {
            if (group.isOwner()) {
                groupRepository.disbandGroup(group);
            } else {
                groupRepository.leaveGroup(group);
            }
        } catch (RuntimeException exception) {
            groupUiState.setValue(UiState.error("Unable to update group."));
        }
    }

    private void mapFriendState(List<Friend> friendList) {
        if (friendList == null) {
            friendUiState.setValue(UiState.error("Unable to load friends."));
            return;
        }
        if (friendList.isEmpty()) {
            friendUiState.setValue(UiState.empty("No friends yet."));
            return;
        }
        friendUiState.setValue(UiState.success(friendList));
    }

    private void mapGroupState(List<Group> groupList) {
        if (groupList == null) {
            groupUiState.setValue(UiState.error("Unable to load groups."));
            return;
        }
        if (groupList.isEmpty()) {
            groupUiState.setValue(UiState.empty("No groups yet."));
            return;
        }
        groupUiState.setValue(UiState.success(groupList));
    }

    private void runFriendAction(FriendAction action, String fallbackErrorCode) {
        friendActionLoading.postValue(true);
        ioExecutor.execute(() -> {
            try {
                String successMessageCode = action.run();
                if (successMessageCode != null && !successMessageCode.isEmpty()) {
                    friendActionMessage.postValue(successMessageCode);
                }
            } catch (IllegalStateException exception) {
                friendActionMessage.postValue(mapActionError(exception.getMessage(), fallbackErrorCode));
            } catch (RuntimeException exception) {
                friendActionMessage.postValue(fallbackErrorCode);
            } finally {
                friendActionLoading.postValue(false);
            }
        });
    }

    private String mapActionError(String errorCode, String fallbackErrorCode) {
        if ("USER_NOT_FOUND".equals(errorCode)) {
            return "__MSG_USER_NOT_FOUND__";
        }
        if ("SELF_REQUEST".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_SELF__";
        }
        if ("REQUEST_PENDING".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_PENDING__";
        }
        if ("ALREADY_FRIENDS".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_ALREADY_FRIENDS__";
        }
        if ("SEND_FAILED".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_SEND_FAILED__";
        }
        if ("UNFRIEND_FAILED".equals(errorCode)) {
            return "__MSG_UNFRIEND_FAILED__";
        }
        if ("ACCEPT_FAILED".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_ACCEPT_FAILED__";
        }
        if ("REJECT_FAILED".equals(errorCode)) {
            return "__MSG_FRIEND_REQUEST_REJECT_FAILED__";
        }
        return fallbackErrorCode;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        ioExecutor.shutdownNow();
    }

    private interface FriendAction {
        String run();
    }
}
