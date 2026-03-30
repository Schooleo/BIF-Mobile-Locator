package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component
public class ProfileSyncEntityHandler implements SyncEntityHandler {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProfileSyncEntityHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public String entityType() {
        return "profile";
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId,
                                    long newVersion) {
        ProfilePayload payload = parseProfilePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        User user = userRepository.findById(userId)
                .orElseGet(() -> {
                    User created = new User();
                    created.setId(userId);
                    return created;
                });

        if ("DELETE".equals(operation)) {
            user.setDeleted(true);
            user.setServerVersion(newVersion);
            user.setLastModifiedBy(userId);
            User saved = userRepository.save(user);

            ProfilePayload responsePayload = toPayload(saved);
            responsePayload.userId = userId;
            responsePayload.deleted = true;
            responsePayload.serverVersion = newVersion;
            return writePayload(responsePayload);
        }

        if (payload != null) {
            if (payload.displayName != null && !payload.displayName.isBlank()) {
                user.setUsername(payload.displayName.trim());
            }
            if (payload.email != null && !payload.email.isBlank()) {
                user.setEmail(payload.email.trim());
            }
            if (payload.avatarLetter != null && !payload.avatarLetter.isBlank()) {
                user.setAvatarLetter(payload.avatarLetter.trim());
            }
            if (payload.avatarColor != null) {
                user.setAvatarColor(payload.avatarColor);
            }
            if (payload.online != null) {
                user.setOnline(payload.online);
            }
            if (payload.deleted != null) {
                user.setDeleted(payload.deleted);
            }
        }

        user.setServerVersion(newVersion);
        user.setLastModifiedBy(userId);
        User saved = userRepository.save(user);

        ProfilePayload responsePayload = toPayload(saved);
        responsePayload.userId = userId;
        responsePayload.serverVersion = newVersion;
        return writePayload(responsePayload);
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        if (entry.getPayload() != null && !entry.getPayload().isBlank()) {
            return entry.getPayload();
        }

        String targetUserId = entry.getUserId();
        if (targetUserId == null || targetUserId.isBlank()) {
            targetUserId = entry.getEntityId();
        }

        if (targetUserId == null || targetUserId.isBlank()) {
            return null;
        }

        Optional<User> userOpt = userRepository.findById(targetUserId);
        if (userOpt.isEmpty()) {
            return null;
        }

        ProfilePayload payload = toPayload(userOpt.get());
        payload.serverVersion = entry.getServerVersion();
        payload.deleted = userOpt.get().isDeleted();
        return writePayload(payload);
    }

    private ProfilePayload parseProfilePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ProfilePayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(ProfilePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private ProfilePayload toPayload(User user) {
        ProfilePayload payload = new ProfilePayload();
        payload.userId = user.getId();
        payload.displayName = user.getUsername();
        payload.email = user.getEmail();
        payload.avatarLetter = user.getAvatarLetter();
        payload.avatarColor = user.getAvatarColor();
        payload.online = user.isOnline();
        payload.serverVersion = user.getServerVersion();
        payload.updatedAt = user.getUpdatedAt() != null
                ? user.getUpdatedAt().toEpochMilli() : 0L;
        payload.deleted = user.isDeleted();
        return payload;
    }

    private static class ProfilePayload {
        public String userId;
        public String displayName;
        public String email;
        public String avatarLetter;
        public Integer avatarColor;
        public Boolean online;
        public Long serverVersion;
        public Long updatedAt;
        public Boolean deleted;
    }
}