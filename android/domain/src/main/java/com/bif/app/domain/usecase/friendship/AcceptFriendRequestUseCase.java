package com.bif.app.domain.usecase.friendship;

import com.bif.app.domain.repository.IFriendshipRepository;

public class AcceptFriendRequestUseCase {
    private final IFriendshipRepository friendshipRepository;

    public AcceptFriendRequestUseCase(IFriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public void execute(int friendshipId) {
        friendshipRepository.acceptFriendRequest(friendshipId);
    }
}