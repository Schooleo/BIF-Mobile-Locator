package com.bif.app.data.mapper;

import com.bif.app.core.network.dto.favorite.FavoriteDto;
import com.bif.app.core.network.dto.favorite.FavoriteLocationDto;
import com.bif.app.core.network.dto.favorite.FavoriteRequestDto;
import com.bif.app.core.network.dto.favorite.FavoriteResponseDto;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.domain.model.Favorite;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FavoriteMapper {

    // Entity -> Domain
    public static Favorite toDomain(FavoriteEntity entity) {
        if (entity == null) return null;
        Favorite domain = new Favorite();
        domain.id = entity.id;
        domain.name = entity.name;
        domain.latitude = entity.latitude;
        domain.longitude = entity.longitude;
        domain.address = entity.address;
        domain.description = entity.description;
        domain.notes = entity.notes;
        domain.rating = entity.rating;
        domain.imagePath = entity.imagePath;
        domain.serverVersion = entity.serverVersion;
        domain.deleted = entity.deleted;
        domain.userId = entity.userId;
        return domain;
    }

    // Domain -> Entity
    public static FavoriteEntity toEntity(Favorite domain) {
        if (domain == null) return null;
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = (domain.id == null || domain.id.trim().isEmpty())
            ? UUID.randomUUID().toString()
            : domain.id;
        entity.name = domain.name;
        entity.latitude = domain.latitude;
        entity.longitude = domain.longitude;
        entity.address = domain.address;
        entity.description = domain.description;
        entity.notes = domain.notes;
        entity.rating = domain.rating;
        entity.imagePath = domain.imagePath;
        entity.serverVersion = domain.serverVersion;
        entity.deleted = domain.deleted;
        entity.userId = domain.userId;
        return entity;
    }

    // Sync DTO -> Entity
    public static FavoriteEntity fromDto(FavoriteDto dto) {
        return fromDto(dto, null);
    }

    public static FavoriteEntity fromDto(FavoriteDto dto, String fallbackUserId) {
        FavoriteEntity entity = new FavoriteEntity();
        String owner = dto.userId != null && !dto.userId.trim().isEmpty()
                ? dto.userId
                : fallbackUserId;
        entity.userId = owner != null ? owner : "anonymous";
        entity.id = dto.id;
        entity.name = dto.name;
        entity.latitude = dto.latitude;
        entity.longitude = dto.longitude;
        entity.address = dto.address;
        entity.description = dto.description;
        entity.notes = dto.notes;
        entity.rating = dto.rating;
        entity.imagePath = dto.imagePath;
        entity.serverVersion = dto.serverVersion;
        entity.deleted = dto.deleted;
        return entity;
    }

    // Domain -> Sync DTO
    public static FavoriteDto toDto(Favorite domain) {
        return toDto(domain, null);
    }

    public static FavoriteDto toDto(Favorite domain, String userId) {
        FavoriteDto dto = new FavoriteDto();
        dto.id = domain.id;
        dto.name = domain.name;
        dto.latitude = domain.latitude;
        dto.longitude = domain.longitude;
        dto.address = domain.address;
        dto.description = domain.description;
        dto.notes = domain.notes;
        dto.rating = domain.rating;
        dto.imagePath = domain.imagePath;
        dto.userId = userId != null ? userId : domain.userId;
        dto.serverVersion = domain.serverVersion;
        dto.deleted = domain.deleted;
        return dto;
    }

    public static List<Favorite> toDomainList(List<FavoriteEntity> entities) {
        List<Favorite> list = new ArrayList<>();
        if (entities != null) {
            for (FavoriteEntity entity : entities) {
                list.add(toDomain(entity));
            }
        }
        return list;
    }

    public static List<FavoriteEntity> toEntityList(List<Favorite> domains) {
        List<FavoriteEntity> list = new ArrayList<>();
        if (domains != null) {
            for (Favorite domain : domains) {
                list.add(toEntity(domain));
            }
        }
        return list;
    }

    public static FavoriteRequestDto toRequestDto(Favorite domain) {
        if (domain == null) return null;
        FavoriteRequestDto dto = new FavoriteRequestDto();
        dto.id = domain.id;
        dto.name = domain.name;
        dto.location = new FavoriteLocationDto(domain.latitude, domain.longitude);
        dto.address = domain.address;
        dto.description = domain.description;
        dto.notes = domain.notes;
        dto.rating = domain.rating;
        dto.imagePath = domain.imagePath;
        return dto;
    }

    public static Favorite toDomain(FavoriteResponseDto dto) {
        if (dto == null) return null;
        Favorite favorite = new Favorite();
        favorite.id = dto.id;
        favorite.name = dto.name;
        favorite.latitude = dto.location != null ? dto.location.latitude : 0;
        favorite.longitude = dto.location != null ? dto.location.longitude : 0;
        favorite.address = dto.address;
        favorite.description = dto.description;
        favorite.notes = dto.notes;
        favorite.rating = dto.rating;
        favorite.imagePath = dto.imagePath;
        return favorite;
    }

    public static List<Favorite> toDomainListFromDto(List<FavoriteResponseDto> dtos) {
        List<Favorite> list = new ArrayList<>();
        if (dtos != null) {
            for (FavoriteResponseDto dto : dtos) {
                list.add(toDomain(dto));
            }
        }
        return list;
    }
}
