package com.bif.server.features.user.services;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void getAll_ReturnsRepositoryData() {
        User user = new User();
        when(userRepository.findAll()).thenReturn(List.of(user));

        List<User> result = userService.getAll();

        assertEquals(1, result.size());
        verify(userRepository).findAll();
    }

    @Test
    void getById_ReturnsOptional() {
        User user = new User();
        when(userRepository.findById("u1")).thenReturn(Optional.of(user));

        Optional<User> result = userService.getById("u1");

        assertTrue(result.isPresent());
        verify(userRepository).findById("u1");
    }

    @Test
    void save_ReturnsSavedEntity() {
        User user = new User();
        when(userRepository.save(user)).thenReturn(user);

        User result = userService.save(user);

        assertSame(user, result);
        verify(userRepository).save(user);
    }

    @Test
    void deleteById_WhenExists_DeletesAndReturnsTrue() {
        when(userRepository.existsById("u1")).thenReturn(true);

        boolean result = userService.deleteById("u1");

        assertTrue(result);
        verify(userRepository).deleteById("u1");
    }

    @Test
    void deleteById_WhenMissing_ReturnsFalse() {
        when(userRepository.existsById("u1")).thenReturn(false);

        boolean result = userService.deleteById("u1");

        assertFalse(result);
        verify(userRepository, never()).deleteById(anyString());
    }
}
