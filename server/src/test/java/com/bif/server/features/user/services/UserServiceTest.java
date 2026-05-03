package com.bif.server.features.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.repositories.UserRepository;

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

    @Test
    void updateMyProfile_WhenUserMissing_ReturnsEmpty() {
        when(userRepository.findById("u1")).thenReturn(Optional.empty());

        Optional<User> result = userService.updateMyProfile("u1", "Alex", "A", 123, null);

        assertTrue(result.isEmpty());
        verify(userRepository).findById("u1");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateMyProfile_WhenNameBlank_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateMyProfile("u1", "   ", "A", 123, null));

        verify(userRepository, never()).findById(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateMyProfile_WhenAvatarLetterBlank_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateMyProfile("u1", "Alex", "   ", 123, null));

        verify(userRepository, never()).findById(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateMyProfile_WhenAvatarUrlInvalid_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> userService.updateMyProfile("u1", "Alex", "A", 123, "notaurl"));

        verify(userRepository, never()).findById(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void updateMyProfile_WhenValid_UpdatesAllowedFieldsOnly() {
        User existing = new User();
        existing.setId("u1");
        existing.setEmail("old@bif.local");
        existing.setOnline(true);
        existing.setServerVersion(10);

        when(userRepository.findById("u1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> result = userService.updateMyProfile("u1", " Alex ", " A ", 0xFF1E88E5, null);

        assertTrue(result.isPresent());
        User saved = result.get();

        assertEquals("Alex", saved.getUsername());
        assertEquals("A", saved.getAvatarLetter());
        assertEquals(0xFF1E88E5, saved.getAvatarColor());
        assertEquals("old@bif.local", saved.getEmail());
        assertTrue(saved.isOnline());
        assertEquals(10, saved.getServerVersion());
        verify(userRepository).save(existing);
    }

    @Test
    void updateMyProfile_WhenAvatarUrlValid_SavesTrimmedUrl() {
        User existing = new User();
        existing.setId("u1");

        when(userRepository.findById("u1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> result = userService.updateMyProfile(
                "u1",
                "Alex",
                "A",
                123,
                " https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg ");

        assertTrue(result.isPresent());
        assertEquals(
                "https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg",
                result.get().getAvatarUrl());
        verify(userRepository).save(existing);
    }

    @Test
    void updateMyProfile_WhenAvatarUrlBlank_ClearsAvatarUrl() {
        User existing = new User();
        existing.setId("u1");
        existing.setAvatarUrl("https://res.cloudinary.com/demo/image/upload/v1/avatar.jpg");

        when(userRepository.findById("u1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<User> result = userService.updateMyProfile(
                "u1",
                "Alex",
                "A",
                123,
                "   ");

        assertTrue(result.isPresent());
        assertEquals(null, result.get().getAvatarUrl());
        verify(userRepository).save(existing);
    }
}
