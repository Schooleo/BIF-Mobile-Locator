package com.bif.server.features.user.controllers;

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
}
