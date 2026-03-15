package com.bif.app.domain.repository;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Friend;

import java.util.List;

public interface IFriendRepository {
    LiveData<List<Friend>> getFriends();
    void addFriend(Friend friend);
    void deleteFriend(Friend friend);
}
