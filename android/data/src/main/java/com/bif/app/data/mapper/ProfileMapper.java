package com.bif.app.data.mapper;

import com.bif.app.core.network.dto.profile.ProfileDto;
import com.bif.app.data.source.local.entity.ProfileEntity;

public final class ProfileMapper {

    private ProfileMapper() {
    }

    public static ProfileEntity fromDto(ProfileDto dto, String fallbackUserId) {
        ProfileEntity entity = new ProfileEntity();
        String owner = dto.userId != null && !dto.userId.trim().isEmpty()
                ? dto.userId
                : fallbackUserId;
        entity.userId = owner != null && !owner.trim().isEmpty()
                ? owner
                : "anonymous";
        entity.displayName = dto.displayName;
        entity.email = dto.email;
        entity.avatarLetter = dto.avatarLetter;
        entity.avatarColor = dto.avatarColor;
        entity.serverVersion = dto.serverVersion;
        entity.updatedAt = dto.updatedAt;
        entity.deleted = dto.deleted;
        return entity;
    }

    public static ProfileDto toDto(ProfileEntity entity) {
        ProfileDto dto = new ProfileDto();
        dto.userId = entity.userId;
        dto.displayName = entity.displayName;
        dto.email = entity.email;
        dto.avatarLetter = entity.avatarLetter;
        dto.avatarColor = entity.avatarColor;
        dto.serverVersion = entity.serverVersion;
        dto.updatedAt = entity.updatedAt;
        dto.deleted = entity.deleted;
        return dto;
    }
}

