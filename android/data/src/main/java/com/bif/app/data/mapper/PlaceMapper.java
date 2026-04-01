package com.bif.app.data.mapper;

import com.bif.app.core.network.dto.place.PlaceDto;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;

import java.util.ArrayList;
import java.util.List;

public class PlaceMapper {

    private PlaceMapper() {
    }

    public static Place toDomain(PlaceEntity entity) {
        return new Place(
                entity.id,
                entity.name,
                entity.address,
                entity.rating,
                new Location(entity.latitude, entity.longitude)
        );
    }

    public static List<Place> toDomainList(List<PlaceEntity> entities) {
        List<Place> result = new ArrayList<>();
        for (PlaceEntity entity : entities) {
            result.add(toDomain(entity));
        }
        return result;
    }

    public static PlaceEntity toEntity(Place place) {
        return toEntity(place, "anonymous");
    }

    public static PlaceEntity toEntity(Place place, String ownerUserId) {
        PlaceEntity entity = new PlaceEntity();
        entity.ownerUserId = ownerUserId;
        entity.id = place.id;
        entity.name = place.name;
        entity.address = place.address;
        entity.rating = place.rating;
        if (place.location != null) {
            entity.latitude = place.location.latitude;
            entity.longitude = place.location.longitude;
        }
        entity.lastSyncedAt = System.currentTimeMillis();
        return entity;
    }

    public static PlaceEntity fromDto(PlaceDto dto) {
        return fromDto(dto, null);
    }

    public static PlaceEntity fromDto(PlaceDto dto, String fallbackOwnerUserId) {
        PlaceEntity entity = new PlaceEntity();
        String owner = dto.persistedByUserId != null
                && !dto.persistedByUserId.trim().isEmpty()
                ? dto.persistedByUserId
                : fallbackOwnerUserId;
        entity.ownerUserId = owner != null ? owner : "anonymous";
        entity.id = dto.id;
        entity.name = dto.name;
        entity.address = dto.address;
        entity.rating = dto.rating;
        entity.latitude = dto.latitude;
        entity.longitude = dto.longitude;
        entity.placeSource = dto.placeSource;
        entity.persistedByAction = dto.persistedByAction;
        entity.serverVersion = dto.serverVersion;
        entity.deleted = dto.deleted;
        entity.lastSyncedAt = System.currentTimeMillis();
        if (dto.tags != null) {
            entity.tags = String.join(",", dto.tags);
        }
        return entity;
    }

    public static PlaceDto toDto(Place place) {
        return toDto(place, null);
    }

    public static PlaceDto toDto(Place place, String persistedByUserId) {
        PlaceDto dto = new PlaceDto();
        dto.id = place.id;
        dto.name = place.name;
        dto.address = place.address;
        dto.rating = place.rating;
        dto.persistedByUserId = persistedByUserId;
        if (place.location != null) {
            dto.latitude = place.location.latitude;
            dto.longitude = place.location.longitude;
        }
        return dto;
    }

    public static Place fromDto(PlaceDto dto, boolean unused) {
        Location loc = new Location(dto.latitude, dto.longitude);
        return new Place(dto.id, dto.name, dto.address, dto.rating, loc);
    }

    public static List<Place> fromDtoList(List<PlaceDto> dtos) {
        List<Place> result = new ArrayList<>();
        for (PlaceDto dto : dtos) {
            result.add(fromDto(dto, true));
        }
        return result;
    }
}

