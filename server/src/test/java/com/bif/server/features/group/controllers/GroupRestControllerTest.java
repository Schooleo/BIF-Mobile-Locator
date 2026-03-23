package com.bif.server.features.group.controllers;

import com.bif.server.features.group.dto.AddMemberRequest;
import com.bif.server.features.group.dto.CreateGroupRequest;
import com.bif.server.features.group.dto.GroupMemberResponse;
import com.bif.server.features.group.dto.UpdateGroupRequest;
import com.bif.server.features.group.dto.UpdateMemberRoleRequest;
import com.bif.server.features.group.exceptions.DuplicateGroupMemberException;
import com.bif.server.features.group.exceptions.GroupMemberNotFoundException;
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
        when(groupService.update("g1", "owner-1", request)).thenReturn(Optional.of(updated));

        ResponseEntity<Group> result = controller.updateGroup("g1", "owner-1", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertSame(updated, result.getBody());
    }

    @Test
    void updateGroup_WhenMissing_ReturnsNotFound() {
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Updated");
        when(groupService.update("g1", "owner-1", request)).thenReturn(Optional.empty());

        ResponseEntity<Group> result = controller.updateGroup("g1", "owner-1", request);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void updateGroup_WhenInvalid_ThrowsBadRequest() {
        UpdateGroupRequest request = new UpdateGroupRequest();
        when(groupService.update("g1", "owner-1", request)).thenThrow(new IllegalArgumentException("name is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.updateGroup("g1", "owner-1", request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

        @Test
        void updateGroup_WhenForbidden_ThrowsForbidden() {
        UpdateGroupRequest request = new UpdateGroupRequest();
        when(groupService.update("g1", "member-1", request)).thenThrow(new SecurityException("admin access required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.updateGroup("g1", "member-1", request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
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
        when(groupService.deleteById("g1", "owner-1")).thenReturn(true);

        ResponseEntity<Void> result = controller.deleteGroup("g1", "owner-1");

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    }

    @Test
    void deleteGroup_WhenMissing_ReturnsNotFound() {
        when(groupService.deleteById("g1", "owner-1")).thenReturn(false);

        ResponseEntity<Void> result = controller.deleteGroup("g1", "owner-1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void deleteGroup_WhenInvalid_ThrowsBadRequest() {
        when(groupService.deleteById(" ", "owner-1")).thenThrow(new IllegalArgumentException("id is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.deleteGroup(" ", "owner-1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

        @Test
        void deleteGroup_WhenForbidden_ThrowsForbidden() {
        when(groupService.deleteById("g1", "member-1")).thenThrow(new SecurityException("owner access required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.deleteGroup("g1", "member-1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

    @Test
    void getGroupMembers_WhenFound_ReturnsOk() {
        when(groupService.getMembers("g1", "member-1"))
                .thenReturn(Optional.of(List.of(new GroupMemberResponse("user-1", "ADMIN"))));

        ResponseEntity<List<GroupMemberResponse>> result = controller.getGroupMembers("g1", "member-1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
    }

    @Test
    void getGroupMembers_WhenMissing_ReturnsNotFound() {
        when(groupService.getMembers("g1", "member-1")).thenReturn(Optional.empty());

        ResponseEntity<List<GroupMemberResponse>> result = controller.getGroupMembers("g1", "member-1");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void getGroupMembers_WhenInvalid_ThrowsBadRequest() {
        when(groupService.getMembers(" ", "member-1")).thenThrow(new IllegalArgumentException("id is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.getGroupMembers(" ", "member-1"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

        @Test
        void getGroupMembers_WhenForbidden_ThrowsForbidden() {
        when(groupService.getMembers("g1", "outsider")).thenThrow(new SecurityException("member access required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.getGroupMembers("g1", "outsider"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

    @Test
    void addMember_WhenFound_ReturnsOk() {
        AddMemberRequest request = new AddMemberRequest();
        request.setMemberId("user-2");
        request.setRole("MEMBER");
        Group group = new Group();
        when(groupService.addMember("g1", "owner-1", request)).thenReturn(Optional.of(group));

        ResponseEntity<Group> result = controller.addMember("g1", "owner-1", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void addMember_WhenMissing_ReturnsNotFound() {
        AddMemberRequest request = new AddMemberRequest();
        request.setMemberId("user-2");
        when(groupService.addMember("g1", "owner-1", request)).thenReturn(Optional.empty());

        ResponseEntity<Group> result = controller.addMember("g1", "owner-1", request);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void addMember_WhenInvalid_ThrowsBadRequest() {
        AddMemberRequest request = new AddMemberRequest();
        when(groupService.addMember("g1", "owner-1", request)).thenThrow(new IllegalArgumentException("memberId is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.addMember("g1", "owner-1", request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void addMember_WhenDuplicate_ThrowsBadRequest() {
        AddMemberRequest request = new AddMemberRequest();
        when(groupService.addMember("g1", "owner-1", request))
                .thenThrow(new DuplicateGroupMemberException("member-1"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.addMember("g1", "owner-1", request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

        @Test
        void addMember_WhenForbidden_ThrowsForbidden() {
        AddMemberRequest request = new AddMemberRequest();
        when(groupService.addMember("g1", "member-1", request)).thenThrow(new SecurityException("admin access required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.addMember("g1", "member-1", request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }

    @Test
    void removeMember_WhenFound_ReturnsOk() {
        Group group = new Group();
        when(groupService.removeMember("g1", "owner-1", "u2")).thenReturn(Optional.of(group));

        ResponseEntity<Group> result = controller.removeMember("g1", "owner-1", "u2");

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void removeMember_WhenMissing_ReturnsNotFound() {
        when(groupService.removeMember("g1", "owner-1", "u2")).thenReturn(Optional.empty());

        ResponseEntity<Group> result = controller.removeMember("g1", "owner-1", "u2");

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void updateMemberRole_WhenFound_ReturnsOk() {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole("ADMIN");
        Group group = new Group();
        when(groupService.updateMemberRole("g1", "owner-1", "u2", request)).thenReturn(Optional.of(group));

        ResponseEntity<Group> result = controller.updateMemberRole("g1", "owner-1", "u2", request);

        assertEquals(HttpStatus.OK, result.getStatusCode());
    }

    @Test
    void updateMemberRole_WhenMissing_ReturnsNotFound() {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole("ADMIN");
        when(groupService.updateMemberRole("g1", "owner-1", "u2", request)).thenReturn(Optional.empty());

        ResponseEntity<Group> result = controller.updateMemberRole("g1", "owner-1", "u2", request);

        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
    }

    @Test
    void updateMemberRole_WhenInvalid_ThrowsBadRequest() {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        when(groupService.updateMemberRole("g1", "owner-1", "u2", request))
                .thenThrow(new IllegalArgumentException("role is required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.updateMemberRole("g1", "owner-1", "u2", request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

        @Test
        void removeMember_WhenMemberNotInGroup_ThrowsNotFound() {
        when(groupService.removeMember("g1", "owner-1", "u2"))
            .thenThrow(new GroupMemberNotFoundException("u2"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.removeMember("g1", "owner-1", "u2"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void updateMemberRole_WhenMemberNotInGroup_ThrowsNotFound() {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        when(groupService.updateMemberRole("g1", "owner-1", "u2", request))
            .thenThrow(new GroupMemberNotFoundException("u2"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.updateMemberRole("g1", "owner-1", "u2", request));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        void updateMemberRole_WhenForbidden_ThrowsForbidden() {
        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        when(groupService.updateMemberRole("g1", "member-1", "u2", request))
            .thenThrow(new SecurityException("admin access required"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> controller.updateMemberRole("g1", "member-1", "u2", request));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        }
}
