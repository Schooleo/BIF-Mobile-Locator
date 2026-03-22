package com.bif.server.features.user.controllers;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class UserGraphqlController {
    private final UserService userService;

    public UserGraphqlController(UserService userService) {
        this.userService = userService;
    }

    @QueryMapping
    public List<User> users() {
        return userService.getAll();
    }

    @QueryMapping
    public User user(@Argument String id) {
        return userService.getById(id).orElse(null);
    }

    @MutationMapping
    public User upsertUser(@Argument User input) {
        return userService.save(input);
    }

    @MutationMapping
    public Boolean deleteUser(@Argument String id) {
        return userService.deleteById(id);
    }

    public record AuthStateResponse(boolean authenticated, String userId, boolean hasProfile) {}

    public record ProfileMetadataResponse(
            String userId,
            String displayName,
            String email,
            String avatarLetter,
            int avatarColor,
            boolean online,
            long serverVersion,
            java.time.Instant updatedAt,
            int profileCompletionPercent
    ) {}

    public record UpdateMyProfileInput(
            String name,
            String avatarLetter,
            Integer avatarColor
    ) {}

    @QueryMapping
    public AuthStateResponse myAuthState(@Argument String userId) {
        if (userId == null || userId.isBlank()) {
            return new AuthStateResponse(false, null, false);
        }
        boolean hasProfile = userService.getById(userId).isPresent();
        return new AuthStateResponse(true, userId, hasProfile);
    }

    @QueryMapping
    public ProfileMetadataResponse myProfileMetadata(@Argument String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        return userService.getById(userId)
                .map(user -> new ProfileMetadataResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getAvatarLetter(),
                        user.getAvatarColor(),
                        user.isOnline(),
                        user.getServerVersion(),
                        user.getUpdatedAt(),
                        calculateProfileCompletion(user)
                ))
                .orElse(null);
    }

    @MutationMapping
    public User updateMyProfile(@Argument String userId, @Argument UpdateMyProfileInput input) {
        if (userId == null || userId.isBlank()) {
        return null;
    }

    return userService.getById(userId)
            .map(user -> {
                if (input.name() != null) {
                    String name = input.name().trim();
                    if (name.isBlank()) {
                        throw new IllegalArgumentException("name must not be blank");
                    }
                    user.setName(name);
                }

                if (input.avatarLetter() != null) {
                    String avatarLetter = input.avatarLetter().trim();
                    if (avatarLetter.isBlank()) {
                        throw new IllegalArgumentException("avatarLetter must not be blank");
                    }
                    user.setAvatarLetter(avatarLetter);
                }

                if (input.avatarColor() != null) {
                    user.setAvatarColor(input.avatarColor());
                }

                return userService.save(user);
            })
            .orElse(null);
    }

    private int calculateProfileCompletion(User user) {
        int completed = 0;
        int total = 4;

        if (user.getName() != null && !user.getName().isBlank()) {
            completed++;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            completed++;
        }
        if (user.getAvatarLetter() != null && !user.getAvatarLetter().isBlank()) {
            completed++;
        }
        if (user.getAvatarColor() != 0) {
            completed++;
        }

        return (completed * 100) / total;
    }
}
