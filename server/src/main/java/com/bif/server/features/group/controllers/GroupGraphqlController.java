package com.bif.server.features.group.controllers;

import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.services.GroupService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class GroupGraphqlController {
    private final GroupService groupService;

    public GroupGraphqlController(GroupService groupService) {
        this.groupService = groupService;
    }

    @QueryMapping
    public List<Group> groups() {
        return groupService.getAll();
    }

    @QueryMapping
    public Group group(@Argument String id) {
        return groupService.getById(id).orElse(null);
    }

    @MutationMapping
    public Group upsertGroup(@Argument Group input) {
        return groupService.save(input);
    }

    @MutationMapping
    public Boolean deleteGroup(@Argument String id) {
        return groupService.deleteById(id);
    }
}
