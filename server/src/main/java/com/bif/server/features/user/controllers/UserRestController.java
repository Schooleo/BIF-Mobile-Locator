package com.bif.server.features.user.controllers;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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

    public record AuthStateResponse(boolean authenticated,
                                    String userId,
                                    boolean hasProfile) 
    {}                        

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

    public record ProfileMetadataResponse(
            String userId,
            String displayName,
            String email,
            String avatarLetter,
            int avatarColor,
            boolean online,
            long serverVersion,
            Instant updatedAt,
            int profileCompletionPercent
    ) {}

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
                        calculateProfileCompletion(user)
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
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

    public record UpdateMyProfileRequest(
        String name,
        String avatarLetter,
        Integer avatarColor,
        String email
        ) {}
    
    @PatchMapping("/me/profile")
    public ResponseEntity<User> updateMyProfile(
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @RequestBody UpdateMyProfileRequest request
    ) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        return userService.getById(userId)
                .map(user -> {
                    if (request.name() != null) {
                        String name = request.name().trim();
                        if (name.isBlank()) {
                            return ResponseEntity.badRequest().<User>build();
                        }
                        user.setName(name);
                    }
                    if (request.avatarLetter() != null) {
                        String avatarLetter = request.avatarLetter().trim();
                        if (avatarLetter.isBlank()) {
                            return ResponseEntity.badRequest().<User>build();
                        }
                        user.setAvatarLetter(avatarLetter);
                    }
                    if (request.avatarColor() != null) {
                        user.setAvatarColor(request.avatarColor());
                    }
                    User updatedUser = userService.save(user);
                    return ResponseEntity.ok(updatedUser);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
