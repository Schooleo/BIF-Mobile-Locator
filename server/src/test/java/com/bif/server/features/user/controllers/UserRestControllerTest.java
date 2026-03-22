package com.bif.server.features.user.controllers;

import com.bif.server.features.user.dto.rest.AuthStateResponse;
import com.bif.server.features.user.dto.rest.ProfileMetadataResponse;
import com.bif.server.features.user.dto.rest.UpdateMyProfileRequest;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRestControllerTest {

    @Mock
    private UserService userService;

    private UserRestController controller;

    @BeforeEach
    void setUp() {
        controller = new UserRestController(userService);
    }

    @Test
    void getUsers_ReturnsData() {
        User user = new User();
        when(userService.getAll()).thenReturn(List.of(user));

        List<User> result = controller.getUsers();

        assertEquals(1, result.size());
        verify(userService).getAll();
    }

    @Test
    void getUserById_WhenFound_ReturnsOk() {
        User user = new User();
        when(userService.getById("u1")).thenReturn(Optional.of(user));

        ResponseEntity<User> result = controller.getUserById("u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(user, result.getBody());
    }

    @Test
    void getUserById_WhenMissing_ReturnsNotFound() {
        when(userService.getById("u1")).thenReturn(Optional.empty());

        ResponseEntity<User> result = controller.getUserById("u1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
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
    void deleteUser_WhenDeleted_ReturnsNoContent() {
        when(userService.deleteById("u1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deleteUser("u1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deleteUser_WhenMissing_ReturnsNotFound() {
        when(userService.deleteById("u1")).thenReturn(false);

        ResponseEntity<Void> result = controller.deleteUser("u1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getMyAuthState_WhenHeaderMissing_ReturnsUnauthorizedAndNotAuthenticated() {
        ResponseEntity<AuthStateResponse> result = controller.getMyAuthState(null);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertFalse(result.getBody().authenticated());
        assertNull(result.getBody().userId());
        assertFalse(result.getBody().hasProfile());
        verify(userService, never()).getById(anyString());
    }

    @Test
    void getMyAuthState_WhenUserHasNoProfile_ReturnsOkAndHasProfileFalse() {
        when(userService.getById("u1")).thenReturn(Optional.empty());

        ResponseEntity<AuthStateResponse> result = controller.getMyAuthState("u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().authenticated());
        assertEquals("u1", result.getBody().userId());
        assertFalse(result.getBody().hasProfile());
        verify(userService).getById("u1");
    }

    @Test
    void getMyAuthState_WhenUserHasProfile_ReturnsOkAndHasProfileTrue() {
        User user = new User();
        when(userService.getById("u1")).thenReturn(Optional.of(user));

        ResponseEntity<AuthStateResponse> result = controller.getMyAuthState("u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().authenticated());
        assertEquals("u1", result.getBody().userId());
        assertTrue(result.getBody().hasProfile());
        verify(userService).getById("u1");
    }

    @Test
    void getMyProfileMetadata_WhenHeaderMissing_ReturnsUnauthorized() {
        ResponseEntity<ProfileMetadataResponse> result =
                controller.getMyProfileMetadata(null);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        assertNull(result.getBody());
        verify(userService, never()).getById(anyString());
    }

    @Test
    void getMyProfileMetadata_WhenUserNotFound_ReturnsNotFound() {
        when(userService.getById("u1")).thenReturn(Optional.empty());

        ResponseEntity<ProfileMetadataResponse> result =
                controller.getMyProfileMetadata("u1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        assertNull(result.getBody());
        verify(userService).getById("u1");
    }

    @Test
    void getMyProfileMetadata_WhenUserFound_ReturnsMetadataWithCompletionPercent() {
        User user = new User();
        user.setId("u1");
        user.setName("Alex");
        user.setEmail("alex@bif.local");
        user.setAvatarLetter("A");
        user.setAvatarColor(0xFF1E88E5);
        user.setOnline(true);
        user.setServerVersion(7);

        when(userService.getById("u1")).thenReturn(Optional.of(user));
        when(userService.calculateProfileCompletion(user)).thenReturn(100);

        ResponseEntity<ProfileMetadataResponse> result =
                controller.getMyProfileMetadata("u1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("u1", result.getBody().userId());
        assertEquals("Alex", result.getBody().displayName());
        assertEquals("alex@bif.local", result.getBody().email());
        assertEquals("A", result.getBody().avatarLetter());
        assertEquals(0xFF1E88E5, result.getBody().avatarColor());
        assertTrue(result.getBody().online());
        assertEquals(7, result.getBody().serverVersion());
        assertEquals(100, result.getBody().profileCompletionPercent());
        verify(userService).getById("u1");
        verify(userService).calculateProfileCompletion(user);
    }

    @Test
    void updateMyProfile_WhenHeaderMissing_ReturnsUnauthorized() {
        UpdateMyProfileRequest request = new UpdateMyProfileRequest("Alex", "A", 123);

        ResponseEntity<User> result = controller.updateMyProfile(null, request);

        assertEquals(HttpStatus.UNAUTHORIZED, result.getStatusCode());
        verify(userService, never()).updateMyProfile(anyString(), any(), any(), any());
    }

    @Test
    void updateMyProfile_WhenUserNotFound_ReturnsNotFound() {
        UpdateMyProfileRequest request = new UpdateMyProfileRequest("Alex", "A", 123);

        when(userService.updateMyProfile("u1", "Alex", "A", 123)).thenReturn(Optional.empty());

        ResponseEntity<User> result = controller.updateMyProfile("u1", request);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        verify(userService).updateMyProfile("u1", "Alex", "A", 123);
    }

    @Test
    void updateMyProfile_WhenServiceThrowsBadInput_ReturnsBadRequest() {
        UpdateMyProfileRequest request = new UpdateMyProfileRequest("   ", "A", 123);

        when(userService.updateMyProfile("u1", "   ", "A", 123))
                .thenThrow(new IllegalArgumentException("name must not be blank"));

        ResponseEntity<User> result = controller.updateMyProfile("u1", request);

        assertEquals(HttpStatus.BAD_REQUEST, result.getStatusCode());
        verify(userService).updateMyProfile("u1", "   ", "A", 123);
    }

    @Test
    void updateMyProfile_WhenValid_ReturnsOk() {
        User saved = new User();
        saved.setId("u1");
        saved.setEmail("old@bif.local");

        UpdateMyProfileRequest request = new UpdateMyProfileRequest(" Alex ", " A ", 0xFF1E88E5);

        when(userService.updateMyProfile("u1", " Alex ", " A ", 0xFF1E88E5)).thenReturn(Optional.of(saved));

        ResponseEntity<User> result = controller.updateMyProfile("u1", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals("u1", result.getBody().getId());
        verify(userService).updateMyProfile("u1", " Alex ", " A ", 0xFF1E88E5);
    }
}
