package com.bif.server.features.group.services;

import com.bif.server.features.group.dto.CreateGroupRequest;
import com.bif.server.features.group.dto.UpdateGroupRequest;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
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
    void getByUserId_ReturnsOwnedAndJoinedGroups() {
        Group group = new Group();
        when(groupRepository.findByOwnerIdOrMemberIdsContaining("u1", "u1")).thenReturn(List.of(group));

        List<Group> result = groupService.getByUserId("u1");

        assertEquals(1, result.size());
        verify(groupRepository).findByOwnerIdOrMemberIdsContaining("u1", "u1");
    }

    @Test
    void getByUserId_WhenBlank_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> groupService.getByUserId("  "));
    }

    @Test
    void create_AssignsOwnerAndMembers() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Weekend Explorers");
        request.setOwnerId("owner-1");
        request.setMemberIds(List.of("member-1", "member-2"));

        Group saved = new Group();
        when(groupRepository.save(any(Group.class))).thenReturn(saved);

        Group result = groupService.create(request);

        assertSame(saved, result);
        verify(groupRepository).save(any(Group.class));
    }

    @Test
    void create_SetsDerivedFieldsAndDefaults() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Group Alpha");
        request.setOwnerId("owner-1");
        request.setMemberIds(Arrays.asList("member-1", "owner-1", " ", null));

        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Group result = groupService.create(request);

        assertEquals("Group Alpha", result.getName());
        assertEquals("G", result.getAvatarLetter());
        assertEquals(0xFF03DAC5, result.getAvatarColor());
        assertEquals("owner-1", result.getOwnerId());
        assertTrue(result.getMemberIds().contains("owner-1"));
        assertTrue(result.getMemberIds().contains("member-1"));
        assertEquals(2, result.getMemberCount());
    }

    @Test
    void create_WithUnsigned32BitAvatarColor_MapsToSignedInt() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Color Group");
        request.setOwnerId("owner-1");
        request.setAvatarColor(4280391411L);

        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Group result = groupService.create(request);

        assertEquals((int) 4280391411L, result.getAvatarColor());
    }

    @Test
    void create_WhenMissingName_ThrowsIllegalArgumentException() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setOwnerId("owner-1");

        assertThrows(IllegalArgumentException.class, () -> groupService.create(request));
    }

    @Test
    void create_WhenMissingOwner_ThrowsIllegalArgumentException() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Group Name");

        assertThrows(IllegalArgumentException.class, () -> groupService.create(request));
    }

    @Test
    void update_WhenFound_UpdatesGroupInfo() {
        Group existing = new Group();
        existing.setId("g1");
        existing.setName("Old Name");
        existing.setAvatarLetter("O");
        existing.setAvatarColor(123);
        when(groupRepository.findById("g1")).thenReturn(Optional.of(existing));
        when(groupRepository.save(existing)).thenReturn(existing);

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("New Name");
        request.setAvatarColor(456L);

        Optional<Group> result = groupService.update("g1", request);

        assertTrue(result.isPresent());
        assertEquals("New Name", result.get().getName());
        assertEquals("N", result.get().getAvatarLetter());
        assertEquals(456, result.get().getAvatarColor());
    }

    @Test
    void update_WhenAvatarColorOutOfRange_ThrowsIllegalArgumentException() {
        Group existing = new Group();
        existing.setId("g1");
        when(groupRepository.findById("g1")).thenReturn(Optional.of(existing));

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setAvatarColor(5000000000L);

        assertThrows(IllegalArgumentException.class, () -> groupService.update("g1", request));
    }

    @Test
    void update_WhenMissing_ReturnsEmpty() {
        when(groupRepository.findById("g1")).thenReturn(Optional.empty());

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("New Name");

        Optional<Group> result = groupService.update("g1", request);

        assertTrue(result.isEmpty());
    }

    @Test
    void update_WhenBlankName_ThrowsIllegalArgumentException() {
        Group existing = new Group();
        existing.setId("g1");
        when(groupRepository.findById("g1")).thenReturn(Optional.of(existing));

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("   ");

        assertThrows(IllegalArgumentException.class, () -> groupService.update("g1", request));
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void getById_WhenBlank_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> groupService.getById("  "));
    }

    @Test
    void deleteById_WhenBlank_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> groupService.deleteById("  "));
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
