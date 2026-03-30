package com.bif.app.data.source.local.entity;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "groups")
public class GroupEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String serverId;
    private String name;
    private String avatarLetter;
    private int avatarColor;
    private boolean isOwner;
    private String ownerId;
    private String memberIdsJson;
    private String memberRolesJson;
    private long serverVersion;
    private boolean deleted;
    private long lastSyncedAt;

    public GroupEntity() {
    }

    @Ignore
    public GroupEntity(int id, String name, String avatarLetter, int avatarColor, boolean isOwner) {
        this.id = id;
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.isOwner = isOwner;
        this.lastSyncedAt = System.currentTimeMillis();
    }

    @Ignore
    public GroupEntity(int id,
                       String serverId,
                       String name,
                       String avatarLetter,
                       int avatarColor,
                       boolean isOwner,
                       String ownerId,
                       String memberIdsJson,
                       String memberRolesJson,
                       long serverVersion,
                       boolean deleted,
                       long lastSyncedAt) {
        this.id = id;
        this.serverId = serverId;
        this.name = name;
        this.avatarLetter = avatarLetter;
        this.avatarColor = avatarColor;
        this.isOwner = isOwner;
        this.ownerId = ownerId;
        this.memberIdsJson = memberIdsJson;
        this.memberRolesJson = memberRolesJson;
        this.serverVersion = serverVersion;
        this.deleted = deleted;
        this.lastSyncedAt = lastSyncedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarLetter() { return avatarLetter; }
    public void setAvatarLetter(String avatarLetter) { this.avatarLetter = avatarLetter; }
    public int getAvatarColor() { return avatarColor; }
    public void setAvatarColor(int avatarColor) { this.avatarColor = avatarColor; }
    public boolean isOwner() { return isOwner; }
    public void setOwner(boolean owner) { isOwner = owner; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getMemberIdsJson() { return memberIdsJson; }
    public void setMemberIdsJson(String memberIdsJson) {
        this.memberIdsJson = memberIdsJson;
    }
    public String getMemberRolesJson() { return memberRolesJson; }
    public void setMemberRolesJson(String memberRolesJson) {
        this.memberRolesJson = memberRolesJson;
    }
    public long getServerVersion() { return serverVersion; }
    public void setServerVersion(long serverVersion) {
        this.serverVersion = serverVersion;
    }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public long getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(long lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}