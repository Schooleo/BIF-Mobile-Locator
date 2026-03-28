package com.bif.server.features.friendship.controllers;

import com.bif.server.features.friendship.dto.CreateFriendRequestDto;
import com.bif.server.features.friendship.dto.FriendshipApiModel;
import com.bif.server.features.friendship.models.Friendship;
import com.bif.server.features.friendship.services.FriendshipService;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/friends")
public class FriendshipRestController {
    private final FriendshipService friendshipService;
    private final UserService userService;

    public FriendshipRestController(FriendshipService friendshipService, UserService userService) {
        this.friendshipService = friendshipService;
        this.userService = userService;
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        return authentication.getPrincipal().toString();
    }

    @GetMapping
    public ResponseEntity<List<User>> getFriends(Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        List<Friendship> friendships = friendshipService.getFriends(userId);
        List<String> friendIds = friendships.stream()
                .map(f -> f.getRequesterId().equals(userId) ? f.getReceiverId() : f.getRequesterId())
                .collect(Collectors.toList());
                
        List<User> friends = friendIds.stream()
                .map(id -> userService.getById(id).orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(friends);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> unfriend(@PathVariable String id, Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        friendshipService.removeFriendship(id, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<List<FriendshipApiModel>> getIncomingRequests(Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<FriendshipApiModel> requests = friendshipService.getIncomingRequests(userId).stream()
                .map(this::mapToApiModel)
                .collect(Collectors.toList());
        return ResponseEntity.ok(requests);
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<List<FriendshipApiModel>> getOutgoingRequests(Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        List<FriendshipApiModel> requests = friendshipService.getOutgoingRequests(userId).stream()
                .map(this::mapToApiModel)
                .collect(Collectors.toList());
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendshipApiModel> sendFriendRequest(@RequestBody CreateFriendRequestDto request, Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        
        try {
            Friendship friendship = friendshipService.sendRequest(userId, request.getReceiverId());
            return ResponseEntity.ok(mapToApiModel(friendship));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<FriendshipApiModel> acceptFriendRequest(@PathVariable String id, Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            Friendship friendship = friendshipService.acceptRequest(id, userId);
            return ResponseEntity.ok(mapToApiModel(friendship));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<FriendshipApiModel> rejectFriendRequest(@PathVariable String id, Authentication authentication) {
        String userId = currentUserId(authentication);
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            Friendship friendship = friendshipService.rejectRequest(id, userId);
            return ResponseEntity.ok(mapToApiModel(friendship));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private FriendshipApiModel mapToApiModel(Friendship friendship) {
        FriendshipApiModel model = new FriendshipApiModel();
        model.setId(friendship.getId());
        model.setRequesterId(friendship.getRequesterId());
        
        User requester = userService.getById(friendship.getRequesterId()).orElse(null);
        model.setRequesterName(requester != null && requester.getUsername() != null ? requester.getUsername() : friendship.getRequesterId());
        
        model.setReceiverId(friendship.getReceiverId());
        model.setStatus(friendship.getStatus().name());
        model.setCreatedAt(friendship.getCreatedAt() != null ? friendship.getCreatedAt().toString() : java.time.Instant.now().toString());
        model.setUpdatedAt(friendship.getUpdatedAt() != null ? friendship.getUpdatedAt().toString() : java.time.Instant.now().toString());
        return model;
    }
}
