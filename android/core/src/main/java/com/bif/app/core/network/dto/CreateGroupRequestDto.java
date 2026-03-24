package com.bif.app.core.network.dto;

import java.util.List;

public class CreateGroupRequestDto {
    public String name;
    public Long avatarColor;
    public String ownerId;
    public List<String> memberIds;
}
