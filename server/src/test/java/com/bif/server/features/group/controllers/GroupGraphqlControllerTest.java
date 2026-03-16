package com.bif.server.features.group.controllers;

import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.services.GroupService;
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
class GroupGraphqlControllerTest {

    @Mock
    private GroupService groupService;

    private GroupGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new GroupGraphqlController(groupService);
    }

    @Test
    void groups_ReturnsData() {
        Group item = new Group();
        when(groupService.getAll()).thenReturn(List.of(item));

        List<Group> result = controller.groups();

        assertEquals(1, result.size());
    }

    @Test
    void group_WhenFound_ReturnsEntity() {
        Group item = new Group();
        when(groupService.getById("g1")).thenReturn(Optional.of(item));

        Group result = controller.group("g1");

        assertSame(item, result);
    }

    @Test
    void group_WhenMissing_ReturnsNull() {
        when(groupService.getById("g1")).thenReturn(Optional.empty());

        assertNull(controller.group("g1"));
    }

    @Test
    void upsertGroup_DelegatesToService() {
        Group input = new Group();
        when(groupService.save(input)).thenReturn(input);

        Group result = controller.upsertGroup(input);

        assertSame(input, result);
    }

    @Test
    void deleteGroup_DelegatesToService() {
        when(groupService.deleteById("g1")).thenReturn(true);

        assertTrue(controller.deleteGroup("g1"));
    }
}
