package com.bif.server.features.group.services;

import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
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
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    private GroupService groupService;

    @BeforeEach
    void setUp() {
        groupService = new GroupService(groupRepository);
    }

    @Test
    void getAll_ReturnsRepositoryData() {
        Group group = new Group();
        when(groupRepository.findAll()).thenReturn(List.of(group));

        List<Group> result = groupService.getAll();

        assertEquals(1, result.size());
        verify(groupRepository).findAll();
    }

    @Test
    void getById_ReturnsOptional() {
        Group group = new Group();
        when(groupRepository.findById("g1")).thenReturn(Optional.of(group));

        Optional<Group> result = groupService.getById("g1");

        assertTrue(result.isPresent());
        verify(groupRepository).findById("g1");
    }

    @Test
    void save_ReturnsSavedEntity() {
        Group group = new Group();
        when(groupRepository.save(group)).thenReturn(group);

        Group result = groupService.save(group);

        assertSame(group, result);
        verify(groupRepository).save(group);
    }

    @Test
    void deleteById_WhenExists_DeletesAndReturnsTrue() {
        when(groupRepository.existsById("g1")).thenReturn(true);

        boolean result = groupService.deleteById("g1");

        assertTrue(result);
        verify(groupRepository).deleteById("g1");
    }

    @Test
    void deleteById_WhenMissing_ReturnsFalse() {
        when(groupRepository.existsById("g1")).thenReturn(false);

        boolean result = groupService.deleteById("g1");

        assertFalse(result);
        verify(groupRepository, never()).deleteById(anyString());
    }
}
