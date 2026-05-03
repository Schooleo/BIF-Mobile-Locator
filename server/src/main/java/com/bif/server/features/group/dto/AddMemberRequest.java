package com.bif.server.features.group.dto;

import lombok.Data;

@Data
public class AddMemberRequest {
    private String memberId;
    private String role;
}