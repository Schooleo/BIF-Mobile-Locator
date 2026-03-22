package com.bif.server.features.user.controllers;

import com.bif.server.features.user.dto.rest.AuthStateResponse;
import com.bif.server.features.user.dto.rest.ProfileMetadataResponse;
import com.bif.server.features.user.dto.rest.UpdateMyProfileRequest;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserRestController {
    private final UserService userService;

    public UserRestController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> getUsers() {
        return userService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable String id) {
        return userService.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public User upsertUser(@RequestBody User user) {
        return userService.save(user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        return userService.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/me/auth-state")
    public ResponseEntity<AuthStateResponse> getMyAuthState(
        @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401)
            .body(new AuthStateResponse(false, null, false));
        }

        boolean hasProfile = userService.getById(userId).isPresent();
        return ResponseEntity.ok(new AuthStateResponse(true, userId, hasProfile));
    }

    @GetMapping("/me/profile-metadata")
    public ResponseEntity<ProfileMetadataResponse> getMyProfileMetadata(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        return userService.getById(userId)
                .map(user -> ResponseEntity.ok(new ProfileMetadataResponse(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getAvatarLetter(),
                        user.getAvatarColor(),
                        user.isOnline(),
                        user.getServerVersion(),
                        user.getUpdatedAt(),
                        userService.calculateProfileCompletion(user)
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PatchMapping("/me/profile")
    public ResponseEntity<User> updateMyProfile(
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestBody UpdateMyProfileRequest request
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        try {
            return userService.updateMyProfile(
                    userId,
                    request.name(),
                    request.avatarLetter(),
                    request.avatarColor()
            ).map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
