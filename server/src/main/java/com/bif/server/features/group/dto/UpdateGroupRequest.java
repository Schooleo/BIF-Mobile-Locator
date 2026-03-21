package com.bif.server.features.group.dto;

import lombok.Data;

@Data
public class UpdateGroupRequest {
    private String name;
    private Long avatarColor;
}