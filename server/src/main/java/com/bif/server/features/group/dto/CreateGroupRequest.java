package com.bif.server.features.group.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateGroupRequest {
    private String name;
    private Long avatarColor;
    private String ownerId;
    private List<String> memberIds;
}