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
            String name,
            String avatarLetter,
            Integer avatarColor
    ) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (name != null && name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (avatarLetter != null && avatarLetter.isBlank()) {
            throw new IllegalArgumentException("avatarLetter must not be blank");
        }

        return userRepository.findById(userId).map(user -> {
            if (name != null) {
                user.setName(name.trim());
            }
            if (avatarLetter != null) {
                user.setAvatarLetter(avatarLetter.trim());
            }
            if (avatarColor != null) {
                user.setAvatarColor(avatarColor);
            }
            return userRepository.save(user);
        });
    }

    public int calculateProfileCompletion(User user) {
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
