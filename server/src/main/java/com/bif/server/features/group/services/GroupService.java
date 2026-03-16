package com.bif.server.features.group.services;

import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GroupService {
    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public List<Group> getAll() {
        return groupRepository.findAll();
    }

    public Optional<Group> getById(String id) {
        return groupRepository.findById(id);
    }

    public Group save(Group group) {
        return groupRepository.save(group);
    }

    public boolean deleteById(String id) {
        if (!groupRepository.existsById(id)) {
            return false;
        }
        groupRepository.deleteById(id);
        return true;
    }
}
