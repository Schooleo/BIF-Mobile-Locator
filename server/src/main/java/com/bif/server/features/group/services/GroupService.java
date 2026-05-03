package com.bif.server.features.group.services;

import com.bif.server.features.group.dto.AddMemberRequest;
import com.bif.server.features.group.dto.CreateGroupRequest;
import com.bif.server.features.group.dto.GroupMemberResponse;
import com.bif.server.features.group.dto.UpdateGroupRequest;
import com.bif.server.features.group.dto.UpdateMemberRoleRequest;
import com.bif.server.features.group.exceptions.DuplicateGroupMemberException;
import com.bif.server.features.group.exceptions.GroupMemberNotFoundException;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class GroupService {
    private static final int DEFAULT_AVATAR_COLOR = 0xFF03DAC5;
    private static final long UNSIGNED_INT_MAX = 0xFFFFFFFFL;
    private static final String ROLE_MEMBER = "MEMBER";
    private static final String ROLE_ADMIN = "ADMIN";

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public List<Group> getAll() {
        return groupRepository.findAll();
    }

    public List<Group> getByUserId(String userId) {
        validateNonBlank(userId, "userId");
        return groupRepository.findByOwnerIdOrMemberIdsContaining(userId.trim(), userId.trim());
    }

    public Optional<Group> getById(String id) {
        validateNonBlank(id, "id");
        return groupRepository.findById(id);
    }

    public Group create(CreateGroupRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        String ownerId = validateNonBlank(request.getOwnerId(), "ownerId");
        String name = validateNonBlank(request.getName(), "name");

        Group group = new Group();
        group.setName(name);
        group.setAvatarLetter(name.substring(0, 1).toUpperCase());
        group.setAvatarColor(request.getAvatarColor() != null
            ? normalizeAvatarColor(request.getAvatarColor())
            : DEFAULT_AVATAR_COLOR);
        group.setOwnerId(ownerId);

        Set<String> members = new LinkedHashSet<>();
        members.add(ownerId);
        if (request.getMemberIds() != null) {
            for (String memberId : request.getMemberIds()) {
                if (memberId != null && !memberId.isBlank()) {
                    members.add(memberId.trim());
                }
            }
        }
        group.setMemberIds(new ArrayList<>(members));
        group.setMemberCount(group.getMemberIds().size());
        group.setMemberRoles(buildInitialMemberRoles(group.getMemberIds(), ownerId));

        return groupRepository.save(group);
    }

    public Optional<Group> addMember(String id, AddMemberRequest request) {
        return addMember(id, null, request);
    }

    public Optional<Group> addMember(String id, String actorId, AddMemberRequest request) {
        validateNonBlank(id, "id");
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        String memberId = validateNonBlank(request.getMemberId(), "memberId");
        String role = normalizeRole(request.getRole(), false);

        Optional<Group> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Group group = existing.get();
        requireAdmin(group, actorId);

        if (group.getMemberIds() == null) {
            group.setMemberIds(new ArrayList<>());
        } else {
            group.setMemberIds(new ArrayList<>(group.getMemberIds()));
        }
        if (group.getMemberRoles() == null) {
            group.setMemberRoles(new HashMap<>());
        } else {
            group.setMemberRoles(new HashMap<>(group.getMemberRoles()));
        }

        if (group.getMemberIds().contains(memberId)) {
            throw new DuplicateGroupMemberException(memberId);
        }

        group.getMemberIds().add(memberId);
        group.getMemberRoles().put(memberId, role);
        group.setMemberCount(group.getMemberIds().size());

        return Optional.of(groupRepository.save(group));
    }

    public Optional<Group> removeMember(String id, String memberId) {
        return removeMember(id, null, memberId);
    }

    public Optional<Group> removeMember(String id, String actorId, String memberId) {
        validateNonBlank(id, "id");
        String memberIdValue = validateNonBlank(memberId, "memberId");

        Optional<Group> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Group group = existing.get();
        requireAdmin(group, actorId);

        if (memberIdValue.equals(group.getOwnerId())) {
            throw new IllegalArgumentException("owner cannot be removed from group");
        }

        if (group.getMemberIds() == null || !group.getMemberIds().contains(memberIdValue)) {
            throw new GroupMemberNotFoundException(memberIdValue);
        }

        if (group.getMemberIds() != null) {
            group.setMemberIds(new ArrayList<>(group.getMemberIds()));
        }
        if (group.getMemberRoles() != null) {
            group.setMemberRoles(new HashMap<>(group.getMemberRoles()));
        }

        if (group.getMemberIds() != null) {
            group.getMemberIds().remove(memberIdValue);
        }
        if (group.getMemberRoles() != null) {
            group.getMemberRoles().remove(memberIdValue);
        }
        group.setMemberCount(group.getMemberIds() != null ? group.getMemberIds().size() : 0);

        return Optional.of(groupRepository.save(group));
    }

    public Optional<Group> updateMemberRole(String id, String memberId, UpdateMemberRoleRequest request) {
        return updateMemberRole(id, null, memberId, request);
    }

    public Optional<Group> updateMemberRole(String id, String actorId, String memberId, UpdateMemberRoleRequest request) {
        validateNonBlank(id, "id");
        String memberIdValue = validateNonBlank(memberId, "memberId");
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        Optional<Group> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Group group = existing.get();
        requireAdmin(group, actorId);

        if (group.getMemberIds() == null || !group.getMemberIds().contains(memberIdValue)) {
            throw new GroupMemberNotFoundException(memberIdValue);
        }

        if (memberIdValue.equals(group.getOwnerId())) {
            throw new IllegalArgumentException("owner role cannot be changed");
        }

        if (group.getMemberRoles() == null) {
            group.setMemberRoles(new HashMap<>());
        }
        group.getMemberRoles().put(memberIdValue, normalizeRole(request.getRole(), true));

        return Optional.of(groupRepository.save(group));
    }

    public Optional<List<GroupMemberResponse>> getMembers(String id) {
        return getMembers(id, null);
    }

    public Optional<List<GroupMemberResponse>> getMembers(String id, String actorId) {
        validateNonBlank(id, "id");
        return groupRepository.findById(id).map(group -> {
            requireMember(group, actorId);

            List<GroupMemberResponse> members = new ArrayList<>();
            List<String> memberIds = group.getMemberIds();
            Map<String, String> memberRoles = group.getMemberRoles();
            if (memberIds == null) {
                return members;
            }

            for (String memberId : memberIds) {
                if (memberId == null || memberId.isBlank()) {
                    continue;
                }

                String role;
                if (memberId.equals(group.getOwnerId())) {
                    role = ROLE_ADMIN;
                } else if (memberRoles != null && memberRoles.containsKey(memberId)) {
                    role = normalizeRole(memberRoles.get(memberId), false);
                } else {
                    role = ROLE_MEMBER;
                }
                members.add(new GroupMemberResponse(memberId, role));
            }

            return members;
        });
    }

    public Optional<Group> update(String id, UpdateGroupRequest request) {
        return update(id, null, request);
    }

    public Optional<Group> update(String id, String actorId, UpdateGroupRequest request) {
        validateNonBlank(id, "id");
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        Optional<Group> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Group group = existing.get();
        requireAdmin(group, actorId);

        if (request.getName() != null) {
            String name = validateNonBlank(request.getName(), "name");
            group.setName(name);
            group.setAvatarLetter(name.substring(0, 1).toUpperCase());
        }
        if (request.getAvatarColor() != null) {
            group.setAvatarColor(normalizeAvatarColor(request.getAvatarColor()));
        }

        return Optional.of(groupRepository.save(group));
    }

    public Group save(Group group) {
        return groupRepository.save(group);
    }

    public boolean deleteById(String id) {
        return deleteById(id, null);
    }

    public boolean deleteById(String id, String actorId) {
        validateNonBlank(id, "id");
        Optional<Group> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        requireOwner(existing.get(), actorId);
        groupRepository.deleteById(id);
        return true;
    }

    private String validateNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private int normalizeAvatarColor(long value) {
        if (value < Integer.MIN_VALUE || value > UNSIGNED_INT_MAX) {
            throw new IllegalArgumentException("avatarColor is out of supported range");
        }
        return (int) value;
    }

    private Map<String, String> buildInitialMemberRoles(List<String> members, String ownerId) {
        Map<String, String> roles = new HashMap<>();
        if (members == null) {
            roles.put(ownerId, ROLE_ADMIN);
            return roles;
        }

        for (String memberId : members) {
            if (memberId == null || memberId.isBlank()) {
                continue;
            }
            roles.put(memberId, memberId.equals(ownerId) ? ROLE_ADMIN : ROLE_MEMBER);
        }
        roles.put(ownerId, ROLE_ADMIN);
        return roles;
    }

    private String normalizeRole(String role, boolean required) {
        if (role == null || role.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("role is required");
            }
            return ROLE_MEMBER;
        }

        String normalized = role.trim().toUpperCase();
        if (!ROLE_MEMBER.equals(normalized) && !ROLE_ADMIN.equals(normalized)) {
            throw new IllegalArgumentException("role must be MEMBER or ADMIN");
        }
        return normalized;
    }

    private void requireMember(Group group, String actorId) {
        String userId = validateNonBlank(actorId, "actorId");
        if (!isMember(group, userId)) {
            throw new SecurityException("member access required");
        }
    }

    private void requireAdmin(Group group, String actorId) {
        String userId = validateNonBlank(actorId, "actorId");
        if (!isAdmin(group, userId)) {
            throw new SecurityException("admin access required");
        }
    }

    private void requireOwner(Group group, String actorId) {
        String userId = validateNonBlank(actorId, "actorId");
        if (!isOwner(group, userId)) {
            throw new SecurityException("owner access required");
        }
    }

    private boolean isMember(Group group, String userId) {
        if (group == null || userId == null || userId.isBlank()) {
            return false;
        }
        if (isOwner(group, userId)) {
            return true;
        }
        return group.getMemberIds() != null && group.getMemberIds().contains(userId);
    }

    private boolean isAdmin(Group group, String userId) {
        if (group == null || userId == null || userId.isBlank()) {
            return false;
        }
        if (isOwner(group, userId)) {
            return true;
        }
        if (group.getMemberRoles() == null) {
            return false;
        }
        String role = group.getMemberRoles().get(userId);
        return ROLE_ADMIN.equals(normalizeRole(role, false));
    }

    private boolean isOwner(Group group, String userId) {
        if (group == null || userId == null || userId.isBlank()) {
            return false;
        }
        return userId.equals(group.getOwnerId());
    }
}
