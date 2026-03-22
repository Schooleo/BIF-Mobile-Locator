package com.bif.server.features.user.controllers;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
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
class UserGraphqlControllerTest {

    @Mock
    private UserService userService;

    private UserGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new UserGraphqlController(userService);
    }

    @Test
    void users_ReturnsData() {
        User user = new User();
        when(userService.getAll()).thenReturn(List.of(user));

        List<User> result = controller.users();

        assertEquals(1, result.size());
    }

    @Test
    void user_WhenFound_ReturnsEntity() {
        User user = new User();
        when(userService.getById("u1")).thenReturn(Optional.of(user));

        User result = controller.user("u1");

        assertSame(user, result);
    }

    @Test
    void user_WhenMissing_ReturnsNull() {
        when(userService.getById("u1")).thenReturn(Optional.empty());

        User result = controller.user("u1");

        assertNull(result);
    }

    @Test
    void upsertUser_DelegatesToService() {
        User input = new User();
        when(userService.save(input)).thenReturn(input);

        User result = controller.upsertUser(input);

        assertSame(input, result);
        verify(userService).save(input);
    }

    @Test
    void deleteUser_DelegatesToService() {
        when(userService.deleteById("u1")).thenReturn(true);

        Boolean result = controller.deleteUser("u1");

        assertTrue(result);
        verify(userService).deleteById("u1");
    }

    @Test
    void myAuthState_WhenUserIdMissing_ReturnsUnauthenticated() {
        UserGraphqlController.AuthStateResponse result = controller.myAuthState(null);

        assertFalse(result.authenticated());
        assertNull(result.userId());
        assertFalse(result.hasProfile());
        verify(userService, never()).getById(anyString());
    }

    @Test
    void myAuthState_WhenUserExists_ReturnsAuthenticatedWithProfile() {
        when(userService.getById("u1")).thenReturn(Optional.of(new User()));

        UserGraphqlController.AuthStateResponse result = controller.myAuthState("u1");

        assertTrue(result.authenticated());
        assertEquals("u1", result.userId());
        assertTrue(result.hasProfile());
        verify(userService).getById("u1");
    }

    @Test
    void myProfileMetadata_WhenUserFound_ReturnsCompletionPercent() {
        User user = new User();
        user.setId("u1");
        user.setName("Alex");
        user.setEmail("alex@bif.local");
        user.setAvatarLetter("A");
        user.setAvatarColor(0xFF1E88E5);
        user.setOnline(true);
        user.setServerVersion(9);

        when(userService.getById("u1")).thenReturn(Optional.of(user));

        UserGraphqlController.ProfileMetadataResponse result = controller.myProfileMetadata("u1");

        assertNotNull(result);
        assertEquals("u1", result.userId());
        assertEquals("Alex", result.displayName());
        assertEquals("alex@bif.local", result.email());
        assertEquals(100, result.profileCompletionPercent());
        verify(userService).getById("u1");
    }

    @Test
    void updateMyProfile_WhenValidInput_UpdatesAllowedFieldsAndKeepsEmail() {
        User existing = new User();
        existing.setId("u1");
        existing.setEmail("old@bif.local");
        existing.setOnline(true);
        existing.setServerVersion(10);

        when(userService.getById("u1")).thenReturn(Optional.of(existing));
        when(userService.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserGraphqlController.UpdateMyProfileInput input =
                new UserGraphqlController.UpdateMyProfileInput(" Alex ", " A ", 0xFF1E88E5);

        User saved = controller.updateMyProfile("u1", input);

        assertNotNull(saved);
        assertEquals("Alex", saved.getName());
        assertEquals("A", saved.getAvatarLetter());
        assertEquals(0xFF1E88E5, saved.getAvatarColor());
        assertEquals("old@bif.local", saved.getEmail());
        assertTrue(saved.isOnline());
        assertEquals(10, saved.getServerVersion());
        verify(userService).save(existing);
    }
}
