package com.bif.server.features.sync.services;

import com.bif.server.features.group.dto.AddMemberRequest;
import com.bif.server.features.group.dto.CreateGroupRequest;
import com.bif.server.features.group.dto.UpdateGroupRequest;
import com.bif.server.features.group.dto.UpdateMemberRoleRequest;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import com.bif.server.features.group.services.GroupService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class GroupSyncEntityHandler implements SyncEntityHandler {

    private final GroupService groupService;
    private final GroupRepository groupRepository;
    private final ObjectMapper objectMapper;

    public GroupSyncEntityHandler(GroupService groupService,
                                  GroupRepository groupRepository,
                                  ObjectMapper objectMapper) {
        this.groupService = groupService;
        this.groupRepository = groupRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String entityType() {
        return "group";
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId,
                                    long newVersion) {
        GroupPayload payload = parsePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        switch (operation) {
            case "CREATE":
                return handleCreate(payload, userId, newVersion, pushed);
            case "DELETE":
                return handleDelete(pushed, payload, userId, newVersion);
            case "ADD_MEMBER":
                return handleAddMember(pushed, payload, userId, newVersion);
            case "REMOVE_MEMBER":
                return handleRemoveMember(pushed, payload, userId, newVersion);
            case "UPDATE_MEMBER_ROLE":
                return handleUpdateMemberRole(pushed, payload, userId,
                        newVersion);
            case "UPDATE":
            default:
                return handleUpdate(pushed, payload, userId, newVersion);
        }
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        Optional<Group> groupOpt = groupRepository.findById(entry.getEntityId());
        if (groupOpt.isEmpty()) {
            return entry.getPayload();
        }

        GroupPayload payload = toPayload(groupOpt.get());
        payload.serverVersion = Math.max(payload.serverVersion,
                entry.getServerVersion());
        return writePayload(payload);
    }

    private String handleCreate(GroupPayload payload, String userId,
                                long newVersion, SyncChange pushed) {
        if (payload == null) {
            return pushed.getPayload();
        }

        CreateGroupRequest request = new CreateGroupRequest();
        request.setName(payload.name);
        request.setAvatarColor(payload.avatarColor);
        request.setOwnerId(payload.ownerId != null && !payload.ownerId.isBlank()
                ? payload.ownerId : userId);
        request.setMemberIds(payload.memberIds);

        Group created = groupService.create(request);
        GroupPayload response = toPayload(created);
        response.serverVersion = newVersion;
        return writePayload(response);
    }

    private String handleUpdate(SyncChange pushed, GroupPayload payload,
                                String userId, long newVersion) {
        String targetId = resolveTargetId(pushed, payload);
        if (targetId == null || targetId.isBlank() || payload == null) {
            return pushed.getPayload();
        }

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName(payload.name);
        request.setAvatarColor(payload.avatarColor);

        Optional<Group> updated = groupService.update(targetId, userId, request);
        if (updated.isEmpty()) {
            return pushed.getPayload();
        }

        GroupPayload response = toPayload(updated.get());
        response.serverVersion = newVersion;
        return writePayload(response);
    }

    private String handleDelete(SyncChange pushed, GroupPayload payload,
                                String userId, long newVersion) {
        String targetId = resolveTargetId(pushed, payload);
        if (targetId == null || targetId.isBlank()) {
            return pushed.getPayload();
        }

        groupService.deleteById(targetId, userId);

        GroupPayload tombstone = new GroupPayload();
        tombstone.id = targetId;
        tombstone.deleted = true;
        tombstone.serverVersion = newVersion;
        return writePayload(tombstone);
    }

    private String handleAddMember(SyncChange pushed, GroupPayload payload,
                                   String userId, long newVersion) {
        String targetId = resolveTargetId(pushed, payload);
        if (targetId == null || targetId.isBlank() || payload == null
                || payload.memberId == null || payload.memberId.isBlank()) {
            return pushed.getPayload();
        }

        AddMemberRequest request = new AddMemberRequest();
        request.setMemberId(payload.memberId);
        request.setRole(payload.role);

        Optional<Group> updated = groupService.addMember(targetId, userId,
                request);
        if (updated.isEmpty()) {
            return pushed.getPayload();
        }

        GroupPayload response = toPayload(updated.get());
        response.serverVersion = newVersion;
        return writePayload(response);
    }

    private String handleRemoveMember(SyncChange pushed, GroupPayload payload,
                                      String userId, long newVersion) {
        String targetId = resolveTargetId(pushed, payload);
        if (targetId == null || targetId.isBlank() || payload == null
                || payload.memberId == null || payload.memberId.isBlank()) {
            return pushed.getPayload();
        }

        Optional<Group> updated = groupService.removeMember(targetId, userId,
                payload.memberId);
        if (updated.isEmpty()) {
            return pushed.getPayload();
        }

        GroupPayload response = toPayload(updated.get());
        response.serverVersion = newVersion;
        return writePayload(response);
    }

    private String handleUpdateMemberRole(SyncChange pushed, GroupPayload payload,
                                          String userId, long newVersion) {
        String targetId = resolveTargetId(pushed, payload);
        if (targetId == null || targetId.isBlank() || payload == null
                || payload.memberId == null || payload.memberId.isBlank()) {
            return pushed.getPayload();
        }

        UpdateMemberRoleRequest request = new UpdateMemberRoleRequest();
        request.setRole(payload.role);

        Optional<Group> updated = groupService.updateMemberRole(targetId, userId,
                payload.memberId, request);
        if (updated.isEmpty()) {
            return pushed.getPayload();
        }

        GroupPayload response = toPayload(updated.get());
        response.serverVersion = newVersion;
        return writePayload(response);
    }

    private String resolveTargetId(SyncChange pushed, GroupPayload payload) {
        String targetId = pushed.getEntityId();
        if ((targetId == null || targetId.isBlank()) && payload != null) {
            targetId = payload.id;
        }
        return targetId;
    }

    private GroupPayload parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, GroupPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(GroupPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private GroupPayload toPayload(Group group) {
        GroupPayload payload = new GroupPayload();
        payload.id = group.getId();
        payload.name = group.getName();
        payload.avatarLetter = group.getAvatarLetter();
        payload.avatarColor = (long) group.getAvatarColor();
        payload.memberCount = group.getMemberCount();
        payload.memberIds = group.getMemberIds();
        payload.memberRoles = group.getMemberRoles();
        payload.ownerId = group.getOwnerId();
        payload.serverVersion = group.getServerVersion();
        payload.deleted = group.isDeleted();
        return payload;
    }

    private static class GroupPayload {
        public String id;
        public String name;
        public String avatarLetter;
        public Long avatarColor;
        public Integer memberCount;
        public List<String> memberIds;
        public Map<String, String> memberRoles;
        public String ownerId;
        public String memberId;
        public String role;
        public long serverVersion;
        public boolean deleted;
    }
}
