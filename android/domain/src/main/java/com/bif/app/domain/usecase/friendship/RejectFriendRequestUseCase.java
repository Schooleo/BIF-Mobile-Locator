package com.bif.app.domain.usecase.friendship;

import com.bif.app.domain.repository.IFriendshipRepository;

public class RejectFriendRequestUseCase {
    private final IFriendshipRepository friendshipRepository;

    public RejectFriendRequestUseCase(IFriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public void execute(int friendshipId) {
        friendshipRepository.rejectFriendRequest(friendshipId);
    }
}