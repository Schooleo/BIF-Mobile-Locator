package com.bif.app.domain.usecase.friendship;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Friend;
import com.bif.app.domain.repository.IFriendshipRepository;

import java.util.List;

public class GetFriendsUseCase {
    private final IFriendshipRepository friendshipRepository;

    public GetFriendsUseCase(IFriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public LiveData<List<Friend>> execute() {
        return friendshipRepository.getFriends();
    }
}