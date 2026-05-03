package com.bif.server.features.user.controllers;

import com.bif.server.features.user.dto.graphql.AuthStateResponse;
import com.bif.server.features.user.dto.graphql.ProfileMetadataResponse;
import com.bif.server.features.user.dto.graphql.UpdateMyProfileInput;
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
                        user.getUsername(),
                        user.getEmail(),
                        user.getAvatarLetter(),
                    user.getAvatarUrl(),
                        user.getAvatarColor(),
                        user.isOnline(),
                        user.getServerVersion(),
                        user.getUpdatedAt(),
                        userService.calculateProfileCompletion(user)
                ))
                .orElse(null);
    }

    @MutationMapping
    public User updateMyProfile(@Argument String userId, @Argument UpdateMyProfileInput input) {
        if (userId == null || userId.isBlank()) {
            return null;
        }

        return userService.updateMyProfile(
                userId,
                input.name(),
                input.avatarLetter(),
            input.avatarColor(),
            input.avatarUrl()
        ).orElse(null);
    }
}
