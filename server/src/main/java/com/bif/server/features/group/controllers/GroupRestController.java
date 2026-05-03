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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/groups")
public class GroupRestController {
    private final GroupService groupService;

    public GroupRestController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<Group> getGroups() {
        return groupService.getAll();
    }

    @GetMapping("/user/{userId}")
    public List<Group> getGroupsByUser(@PathVariable String userId) {
        try {
            return groupService.getByUserId(userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Group> getGroupById(@PathVariable String id) {
        try {
            return groupService.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<Group> createGroup(@RequestBody CreateGroupRequest request) {
        try {
            return ResponseEntity.ok(groupService.create(request));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Group> updateGroup(
            @PathVariable String id,
            @RequestParam String actorId,
            @RequestBody UpdateGroupRequest request
    ) {
        try {
            return groupService.update(id, actorId, request)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Group> patchGroup(
            @PathVariable String id,
            @RequestParam String actorId,
            @RequestBody UpdateGroupRequest request
    ) {
        try {
            return groupService.update(id, actorId, request)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<GroupMemberResponse>> getGroupMembers(
            @PathVariable String id,
            @RequestParam String actorId
    ) {
        try {
            return groupService.getMembers(id, actorId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<Group> addMember(
            @PathVariable String id,
            @RequestParam String actorId,
            @RequestBody AddMemberRequest request
    ) {
        try {
            return groupService.addMember(id, actorId, request)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage());
        } catch (DuplicateGroupMemberException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public ResponseEntity<Group> removeMember(
            @PathVariable String id,
            @RequestParam String actorId,
            @PathVariable String memberId
    ) {
        try {
            return groupService.removeMember(id, actorId, memberId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage());
        } catch (GroupMemberNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PatchMapping("/{id}/members/{memberId}/role")
    public ResponseEntity<Group> updateMemberRole(
            @PathVariable String id,
            @RequestParam String actorId,
            @PathVariable String memberId,
            @RequestBody UpdateMemberRoleRequest request
    ) {
        try {
            return groupService.updateMemberRole(id, actorId, memberId, request)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage());
        } catch (GroupMemberNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/upsert")
    public Group upsertGroup(@RequestBody Group group) {
        return groupService.save(group);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGroup(@PathVariable String id, @RequestParam String actorId) {
        try {
            return groupService.deleteById(id, actorId)
                    ? ResponseEntity.noContent().build()
                    : ResponseEntity.notFound().build();
        } catch (SecurityException ex) {
            throw new ResponseStatusException(FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }
}
