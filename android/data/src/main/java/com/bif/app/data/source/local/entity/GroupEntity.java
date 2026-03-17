package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "groups")
public class GroupEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private boolean isOwner;

    public GroupEntity(int id, String name, String avatarLetter, int avatarColor, boolean isOwner) {
        this.id = id;
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.isOwner = isOwner;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarLetter() { return avatarLetter; }
    public void setAvatarLetter(String avatarLetter) { this.avatarLetter = avatarLetter; }
    public int getAvatarColor() { return avatarColor; }
    public void setAvatarColor(int avatarColor) { this.avatarColor = avatarColor; }
    public boolean isOwner() { return isOwner; }
    public void setOwner(boolean owner) { isOwner = owner; }
}