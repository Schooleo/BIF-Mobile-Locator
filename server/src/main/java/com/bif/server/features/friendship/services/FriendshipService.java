package com.bif.server.features.friendship.services;

import com.bif.server.features.friendship.models.Friendship;
import com.bif.server.features.friendship.models.FriendshipStatus;
import com.bif.server.features.friendship.repositories.FriendshipRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;

    public FriendshipService(FriendshipRepository friendshipRepository) {
        this.friendshipRepository = friendshipRepository;
    }

    public Friendship sendRequest(String requesterId, String receiverId) {
        if (requesterId.equals(receiverId)) {
            throw new IllegalArgumentException("Cannot send friend request to yourself");
        }
        
        List<Friendship> existing1 = friendshipRepository.findByRequesterIdAndReceiverId(requesterId, receiverId);
        if (!existing1.isEmpty()) {
            Friendship last = existing1.get(existing1.size() - 1);
            if (last.getStatus() == FriendshipStatus.PENDING || last.getStatus() == FriendshipStatus.ACCEPTED) {
                return last;
            }
        }
        
        List<Friendship> existing2 = friendshipRepository.findByRequesterIdAndReceiverId(receiverId, requesterId);
        if (!existing2.isEmpty()) {
            Friendship last = existing2.get(existing2.size() - 1);
            if (last.getStatus() == FriendshipStatus.PENDING || last.getStatus() == FriendshipStatus.ACCEPTED) {
                return last;
            }
        }

        Friendship friendship = new Friendship();
        friendship.setRequesterId(requesterId);
        friendship.setReceiverId(receiverId);
        friendship.setStatus(FriendshipStatus.PENDING);
        return friendshipRepository.save(friendship);
    }

    public Friendship acceptRequest(String id, String receiverId) {
        return friendshipRepository.findById(id).map(f -> {
            boolean isReceiver = f.getReceiverId().equals(receiverId);
            if (isReceiver && f.getStatus() == FriendshipStatus.PENDING) {
                f.setStatus(FriendshipStatus.ACCEPTED);
                return friendshipRepository.save(f);
            }
            return f;
        }).orElseThrow(() -> new IllegalArgumentException("Friendship not found"));
    }

    public Friendship rejectRequest(String id, String receiverId) {
         return friendshipRepository.findById(id).map(f -> {
            boolean isReceiver = f.getReceiverId().equals(receiverId);
            if (isReceiver && f.getStatus() == FriendshipStatus.PENDING) {
                f.setStatus(FriendshipStatus.REJECTED);
                return friendshipRepository.save(f);
            }
            return f;
        }).orElseThrow(() -> new IllegalArgumentException("Friendship not found"));
    }

    public List<Friendship> getIncomingRequests(String userId) {
        return friendshipRepository.findByReceiverIdAndStatus(userId, FriendshipStatus.PENDING);
    }

    public List<Friendship> getOutgoingRequests(String userId) {
        return friendshipRepository.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING);
    }

    public List<Friendship> getFriends(String userId) {
        return friendshipRepository.findByRequesterIdOrReceiverId(userId, userId).stream()
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .collect(Collectors.toList());
    }

    public void removeFriendship(String friendId, String currentUserId) {
        List<Friendship> f1 = friendshipRepository.findByRequesterIdAndReceiverId(currentUserId, friendId);
        List<Friendship> f2 = friendshipRepository.findByRequesterIdAndReceiverId(friendId, currentUserId);
        
        for (Friendship f : f1) {
            f.setStatus(FriendshipStatus.CANCELED);
            friendshipRepository.save(f);
        }
        for (Friendship f : f2) {
            f.setStatus(FriendshipStatus.CANCELED);
            friendshipRepository.save(f);
        }
    }
}
