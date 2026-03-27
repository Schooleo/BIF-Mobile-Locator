package com.bif.server.features.friendship.repositories;

import com.bif.server.features.friendship.models.Friendship;
import com.bif.server.features.friendship.models.FriendshipStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends MongoRepository<Friendship, String> {
    List<Friendship> findByRequesterIdAndStatus(String requesterId, FriendshipStatus status);
    List<Friendship> findByReceiverIdAndStatus(String receiverId, FriendshipStatus status);
    List<Friendship> findByRequesterIdAndReceiverId(String requesterId, String receiverId);
    Optional<Friendship> findByRequesterIdAndReceiverIdAndStatus(String requesterId, String receiverId, FriendshipStatus status);
    List<Friendship> findByRequesterIdOrReceiverId(String requesterId, String receiverId);
}
