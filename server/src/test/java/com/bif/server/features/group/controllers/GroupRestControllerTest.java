package com.bif.server.features.group.controllers;

import com.bif.server.features.group.dto.CreateGroupRequest;
import com.bif.server.features.group.dto.UpdateGroupRequest;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.services.GroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

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
    void getGroupsByUser_ReturnsData() {
        Group item = new Group();
        when(groupService.getByUserId("u1")).thenReturn(List.of(item));

        List<Group> result = controller.getGroupsByUser("u1");

        assertEquals(1, result.size());
    }

    @Test
    void getGroupsByUser_WhenInvalid_ThrowsBadRequest() {
        when(groupService.getByUserId(" ")).thenThrow(new IllegalArgumentException("userId is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.getGroupsByUser(" "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void createGroup_DelegatesToService() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Team");
        request.setOwnerId("owner-1");

        Group saved = new Group();
        when(groupService.create(request)).thenReturn(saved);

        ResponseEntity<Group> result = controller.createGroup(request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(saved, result.getBody());
    }

    @Test
    void createGroup_WhenInvalid_ThrowsBadRequest() {
        CreateGroupRequest request = new CreateGroupRequest();
        when(groupService.create(request)).thenThrow(new IllegalArgumentException("name is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.createGroup(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void updateGroup_WhenFound_ReturnsOk() {
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Updated");
        Group updated = new Group();
        when(groupService.update("g1", request)).thenReturn(Optional.of(updated));

        ResponseEntity<Group> result = controller.updateGroup("g1", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(updated, result.getBody());
    }

    @Test
    void updateGroup_WhenMissing_ReturnsNotFound() {
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Updated");
        when(groupService.update("g1", request)).thenReturn(Optional.empty());

        ResponseEntity<Group> result = controller.updateGroup("g1", request);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void updateGroup_WhenInvalid_ThrowsBadRequest() {
        UpdateGroupRequest request = new UpdateGroupRequest();
        when(groupService.update("g1", request)).thenThrow(new IllegalArgumentException("name is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.updateGroup("g1", request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
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

    @Test
    void deleteGroup_WhenInvalid_ThrowsBadRequest() {
        when(groupService.deleteById(" ")).thenThrow(new IllegalArgumentException("id is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.deleteGroup(" "));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
