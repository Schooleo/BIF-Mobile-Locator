package com.bif.app.data.mapper;

import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.domain.model.Favorite;

import java.util.ArrayList;
import java.util.List;

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
        return domain;
    }

    // Domain -> Entity
    public static FavoriteEntity toEntity(Favorite domain) {
        if (domain == null) return null;
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = domain.id;
        entity.name = domain.name;
        entity.latitude = domain.latitude;
        entity.longitude = domain.longitude;
        entity.address = domain.address;
        entity.description = domain.description;
        entity.notes = domain.notes;
        entity.rating = domain.rating;
        entity.imagePath = domain.imagePath;
        return entity;
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
}