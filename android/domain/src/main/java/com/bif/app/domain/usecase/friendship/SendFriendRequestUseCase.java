package com.bif.app.domain.usecase.friendship;

import com.bif.app.domain.repository.IFriendshipRepository;

public class SendFriendRequestUseCase {
    private final IFriendshipRepository friendshipRepository;

    public SendFriendRequestUseCase(IFriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public void execute(String receiverId) {
        friendshipRepository.sendFriendRequest(receiverId);
    }
}