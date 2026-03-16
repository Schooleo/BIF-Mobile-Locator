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
}
