package com.bif.server.features.group.services;

import com.bif.server.features.group.dto.CreateGroupRequest;
import com.bif.server.features.group.dto.UpdateGroupRequest;
import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GroupService {
    private static final int DEFAULT_AVATAR_COLOR = 0xFF03DAC5;
    private static final long UNSIGNED_INT_MAX = 0xFFFFFFFFL;

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

        return groupRepository.save(group);
    }

    public Optional<Group> update(String id, UpdateGroupRequest request) {
        validateNonBlank(id, "id");
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }

        Optional<Group> existing = groupRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        Group group = existing.get();
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
        validateNonBlank(id, "id");
        if (!groupRepository.existsById(id)) {
            return false;
        }
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
}
