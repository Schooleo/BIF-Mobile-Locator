package com.bif.server.features.user.services;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
            String avatarUrl
    ) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (username != null && username.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (avatarLetter != null && avatarLetter.isBlank()) {
            throw new IllegalArgumentException("avatarLetter must not be blank");
        }
        if (avatarUrl != null && avatarUrl.isBlank()) {
            throw new IllegalArgumentException("avatarUrl must not be blank");
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
            if (avatarUrl != null) {
                user.setAvatarUrl(avatarUrl.trim());
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
}
