package com.bif.server.features.user.controllers;

import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class UserGraphqlController {
    private final UserService userService;

    public UserGraphqlController(UserService userService) {
        this.userService = userService;
    }

    @QueryMapping
    public List<User> users() {
        return userService.getAll();
    }

    @QueryMapping
    public User user(@Argument String id) {
        return userService.getById(id).orElse(null);
    }

    @MutationMapping
    public User upsertUser(@Argument User input) {
        return userService.save(input);
    }

    @MutationMapping
    public Boolean deleteUser(@Argument String id) {
        return userService.deleteById(id);
    }
}
