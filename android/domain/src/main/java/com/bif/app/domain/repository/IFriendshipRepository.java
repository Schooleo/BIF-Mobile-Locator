package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;

import java.util.List;

public interface IFriendshipRepository {
    LiveData<List<Friendship>> getPendingRequests();
    LiveData<List<Friend>> getFriends();

    void sendFriendRequest(String receiverId);
    void acceptFriendRequest(int friendshipId);
    void rejectFriendRequest(int friendshipId);

    void refreshPendingRequests();
    void refreshFriends();
}