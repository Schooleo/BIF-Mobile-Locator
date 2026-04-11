package com.bif.server.features.sync.services;

import com.bif.server.features.friendship.dto.FriendshipApiModel;
import com.bif.server.features.friendship.models.Friendship;
import com.bif.server.features.friendship.repositories.FriendshipRepository;
import com.bif.server.features.friendship.services.FriendshipService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

@Component
public class FriendshipSyncEntityHandler implements SyncEntityHandler {

    private final FriendshipService friendshipService;
    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public FriendshipSyncEntityHandler(FriendshipService friendshipService,
                                       FriendshipRepository friendshipRepository,
                                       UserService userService,
                                       ObjectMapper objectMapper) {
        this.friendshipService = friendshipService;
        this.friendshipRepository = friendshipRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String entityType() {
        return "friendship";
    }

    @Override
    public SyncPushApplyResult applyPushedChangeResult(SyncChange pushed,
                                                       String userId,
                                                       LongSupplier nextVersionSupplier) {
        FriendshipPayload payload = parsePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null ? pushed.getOperation().trim() : "";

        Friendship friendship;
        switch (operation) {
            case "SEND_REQUEST":
                if (payload == null || isBlank(payload.requesterId) || isBlank(payload.receiverId)) {
                    return SyncPushApplyResult.rejectedValidation("INVALID_FRIENDSHIP_PAYLOAD");
                }
                friendship = friendshipService.sendRequest(payload.requesterId, payload.receiverId);
                break;
            case "ACCEPT_REQUEST":
                if (isBlank(pushed.getEntityId())) {
                    return SyncPushApplyResult.rejectedValidation("MISSING_FRIENDSHIP_ID");
                }
                friendship = friendshipService.acceptRequest(pushed.getEntityId(), userId);
                break;
            case "REJECT_REQUEST":
                if (isBlank(pushed.getEntityId())) {
                    return SyncPushApplyResult.rejectedValidation("MISSING_FRIENDSHIP_ID");
                }
                friendship = friendshipService.rejectRequest(pushed.getEntityId(), userId);
                break;
            default:
                return SyncPushApplyResult.rejectedValidation("UNSUPPORTED_FRIENDSHIP_OPERATION");
        }

        long newVersion = nextVersionSupplier.getAsLong();
        friendship.setServerVersion(newVersion);
        friendship.setLastModifiedBy(userId);
        friendship = friendshipRepository.save(friendship);
        return SyncPushApplyResult.applied(writePayload(toApiModel(friendship)), newVersion);
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId, long newVersion) {
        return applyPushedChangeResult(pushed, userId, () -> newVersion)
                .getPayload();
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        if (isBlank(entry.getEntityId())) {
            return entry.getPayload();
        }

        Optional<Friendship> friendship = friendshipRepository.findById(entry.getEntityId());
        if (friendship.isEmpty()) {
            return entry.getPayload();
        }
        return writePayload(toApiModel(friendship.get()));
    }

    private FriendshipPayload parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            if (json.trim().startsWith("{")) {
                return objectMapper.readValue(json, FriendshipPayload.class);
            }
            Map<?, ?> fallback = objectMapper.readValue(json, Map.class);
            FriendshipPayload payload = new FriendshipPayload();
            Object requesterId = fallback.get("requesterId");
            Object receiverId = fallback.get("receiverId");
            payload.requesterId = requesterId instanceof String ? (String) requesterId : null;
            payload.receiverId = receiverId instanceof String ? (String) receiverId : null;
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(FriendshipApiModel model) {
        try {
            return objectMapper.writeValueAsString(model);
        } catch (Exception e) {
            return null;
        }
    }

    private FriendshipApiModel toApiModel(Friendship friendship) {
        FriendshipApiModel model = new FriendshipApiModel();
        model.setId(friendship.getId());
        model.setRequesterId(friendship.getRequesterId());
        User requester = userService.getById(friendship.getRequesterId()).orElse(null);
        model.setRequesterName(requester != null && requester.getUsername() != null
                ? requester.getUsername()
                : friendship.getRequesterId());
        model.setReceiverId(friendship.getReceiverId());
        model.setStatus(friendship.getStatus() != null ? friendship.getStatus().name() : "");
        model.setCreatedAt(friendship.getCreatedAt() != null
                ? friendship.getCreatedAt().toString()
                : Instant.now().toString());
        model.setUpdatedAt(friendship.getUpdatedAt() != null
                ? friendship.getUpdatedAt().toString()
                : Instant.now().toString());
        return model;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class FriendshipPayload {
        public String requesterId;
        public String receiverId;
    }
}
