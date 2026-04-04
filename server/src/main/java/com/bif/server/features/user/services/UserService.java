package com.bif.server.features.user.services;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public Optional<User> getById(String id) {
        return userRepository.findById(id);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public boolean deleteById(String id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        userRepository.deleteById(id);
        return true;
    }

    public Optional<User> updateMyProfile(
            String userId,
            String username,
            String avatarLetter,
            Integer avatarColor,
            String avatarUrl) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (username != null && username.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (avatarLetter != null && avatarLetter.isBlank()) {
            throw new IllegalArgumentException("avatarLetter must not be blank");
        }
        boolean avatarUrlProvided = avatarUrl != null;
        String normalizedAvatarUrl = avatarUrlProvided ? avatarUrl.trim() : null;
        if (avatarUrlProvided
                && !normalizedAvatarUrl.isEmpty()
                && !isValidHttpUrl(normalizedAvatarUrl)) {
            throw new IllegalArgumentException("avatarUrl must be a valid http(s) URL");
        }

        return userRepository.findById(userId).map(user -> {
            if (username != null) {
                user.setUsername(username.trim());
            }
            if (avatarLetter != null) {
                user.setAvatarLetter(avatarLetter.trim());
            }
            if (avatarColor != null) {
                user.setAvatarColor(avatarColor);
            }
            if (avatarUrlProvided) {
                user.setAvatarUrl(normalizedAvatarUrl.isEmpty()
                        ? null
                        : normalizedAvatarUrl);
            }
            return userRepository.save(user);
        });
    }

    public int calculateProfileCompletion(User user) {
        int completed = 0;
        int total = 5;

        if (user.getUsername() != null && !user.getUsername().isBlank()) {
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
        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            completed++;
        }

        return (completed * 100) / total;
    }

    private boolean isValidHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null
                    && ("http".equalsIgnoreCase(scheme)
                            || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
