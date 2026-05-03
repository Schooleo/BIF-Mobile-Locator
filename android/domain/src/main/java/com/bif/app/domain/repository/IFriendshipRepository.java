package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;

import java.util.List;

public interface IFriendshipRepository {
    LiveData<List<Friendship>> getPendingRequests();
    LiveData<List<Friendship>> getOutgoingRequests();
    LiveData<List<Friend>> getFriends();

    String resolveUserId(String query);
    void sendFriendRequest(String receiverId);
    void unfriend(String friendId);
    void acceptFriendRequest(int friendshipId);
    void rejectFriendRequest(int friendshipId);

    void refreshPendingRequests();
    void refreshOutgoingRequests();
    void refreshFriends();
}