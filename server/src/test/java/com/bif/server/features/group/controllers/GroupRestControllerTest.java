package com.bif.server.features.group.controllers;

import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.services.GroupService;
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
class GroupRestControllerTest {

    @Mock
    private GroupService groupService;

    private GroupRestController controller;

    @BeforeEach
    void setUp() {
        controller = new GroupRestController(groupService);
    }

    @Test
    void getGroups_ReturnsData() {
        Group item = new Group();
        when(groupService.getAll()).thenReturn(List.of(item));

        List<Group> result = controller.getGroups();

        assertEquals(1, result.size());
    }

    @Test
    void getGroupById_WhenFound_ReturnsOk() {
        Group item = new Group();
        when(groupService.getById("g1")).thenReturn(Optional.of(item));

        ResponseEntity<Group> result = controller.getGroupById("g1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(item, result.getBody());
    }

    @Test
    void getGroupById_WhenMissing_ReturnsNotFound() {
        when(groupService.getById("g1")).thenReturn(Optional.empty());

        ResponseEntity<Group> result = controller.getGroupById("g1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void upsertGroup_DelegatesToService() {
        Group input = new Group();
        when(groupService.save(input)).thenReturn(input);

        Group result = controller.upsertGroup(input);

        assertSame(input, result);
    }

    @Test
    void deleteGroup_WhenDeleted_ReturnsNoContent() {
        when(groupService.deleteById("g1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deleteGroup("g1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deleteGroup_WhenMissing_ReturnsNotFound() {
        when(groupService.deleteById("g1")).thenReturn(false);

        ResponseEntity<Void> result = controller.deleteGroup("g1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }
}
