package com.bif.app.domain.usecase.friendship;

import androidx.lifecycle.LiveData;

import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.repository.IFriendshipRepository;

import java.util.List;

public class GetPendingFriendRequestsUseCase {
    private final IFriendshipRepository friendshipRepository;

    public GetPendingFriendRequestsUseCase(IFriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public LiveData<List<Friendship>> execute() {
        return friendshipRepository.getPendingRequests();
    }
}