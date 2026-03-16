package com.bif.app.domain.model;

import java.util.List;

public class Group {
    private int id;
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private List<Friend> members;
    private boolean isOwner;


    public Group(int id, String name, String avatarLetter, int avatarColor, List<Friend> members, boolean isOwner) {
        this.id = id;
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.members = members;
        this.isOwner = isOwner;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getAvatarLetter() { return avatarLetter; }
    public int getAvatarColor() { return avatarColor; }
    public List<Friend> getMembers() { return members; }
    public boolean isOwner() { return isOwner; }

    public int getMemberCount() {
        return members != null ? members.size() : 0;
    }
}
