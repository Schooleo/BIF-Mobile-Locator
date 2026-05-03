package com.bif.app.data.mapper;

import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.domain.model.Friend;

import java.util.ArrayList;
import java.util.List;

public class FriendMapper {
    public static Friend toDomain(FriendEntity entity) {
        return new Friend(
                entity.id,
                entity.serverUserId,
                entity.name,
                entity.avatarLetter,
                entity.avatarColor,
                entity.isOnline
        );
    }

    public static FriendEntity toEntity(Friend domain) {
        FriendEntity entity = new FriendEntity();
        entity.id = domain.getId();
        entity.serverUserId = domain.getServerUserId();
        entity.name = domain.getName();
        entity.avatarLetter = domain.getAvatarLetter();
        entity.avatarColor = domain.getAvatarColor();
        entity.isOnline = domain.isOnline();
        return entity;
    }

    public static List<Friend> toDomainList(List<FriendEntity> entities) {
        List<Friend> list = new ArrayList<>();
        if (entities != null) {
            for (FriendEntity entity : entities) {
                list.add(toDomain(entity));
            }
        }
        return list;
    }
}
