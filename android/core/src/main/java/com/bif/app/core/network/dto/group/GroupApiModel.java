package com.bif.app.core.network.dto.group;

import java.util.List;
import java.util.Map;

public class GroupApiModel {
    public String id;
    public String name;
    public String avatarLetter;
    public int avatarColor;
    public int memberCount;
    public List<String> memberIds;
    public Map<String, String> memberRoles;
    public String ownerId;
}

