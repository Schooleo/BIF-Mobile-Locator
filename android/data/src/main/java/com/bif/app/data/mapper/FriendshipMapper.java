package com.bif.app.data.mapper;

import com.bif.app.core.network.dto.friendship.FriendshipApiModel;
import com.bif.app.data.source.local.entity.FriendshipEntity;
import com.bif.app.domain.model.Friendship;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class FriendshipMapper {

    private FriendshipMapper() {
    }

    public static Friendship toDomain(FriendshipEntity entity) {
        return new Friendship(
                entity.id,
                entity.requesterId,
                entity.receiverId,
                toDomainStatus(entity.status != null ? entity.status.name() : null),
                entity.createdAt,
                entity.updatedAt
        );
    }

    public static List<Friendship> toDomainList(List<FriendshipEntity> entities) {
        List<Friendship> result = new ArrayList<>();
        if (entities == null) {
            return result;
        }
        for (FriendshipEntity entity : entities) {
            result.add(toDomain(entity));
        }
        return result;
    }

    public static FriendshipEntity fromApi(FriendshipApiModel apiModel) {
        FriendshipEntity entity = new FriendshipEntity();
        entity.id = stableId(apiModel != null ? apiModel.id : null);
        entity.requesterId = apiModel != null ? apiModel.requesterId : null;
        entity.receiverId = apiModel != null ? apiModel.receiverId : null;
        entity.status = toLocalStatus(apiModel != null ? apiModel.status : null);
        entity.createdAt = parseInstantToMillis(apiModel != null ? apiModel.createdAt : null);
        entity.updatedAt = parseInstantToMillis(apiModel != null ? apiModel.updatedAt : null);
        if (entity.updatedAt <= 0L) {
            entity.updatedAt = entity.createdAt > 0L
                    ? entity.createdAt
                    : System.currentTimeMillis();
        }
        return entity;
    }

    public static List<FriendshipEntity> fromApiList(List<FriendshipApiModel> apiModels) {
        List<FriendshipEntity> result = new ArrayList<>();
        if (apiModels == null) {
            return result;
        }
        for (FriendshipApiModel apiModel : apiModels) {
            result.add(fromApi(apiModel));
        }
        return result;
    }

    private static com.bif.app.data.source.local.entity.FriendshipStatus toLocalStatus(String rawStatus) {
        String normalized = normalizeStatus(rawStatus);
        if (normalized == null) {
            return com.bif.app.data.source.local.entity.FriendshipStatus.PENDING;
        }
        try {
            return com.bif.app.data.source.local.entity.FriendshipStatus.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return com.bif.app.data.source.local.entity.FriendshipStatus.PENDING;
        }
    }

    private static com.bif.app.domain.model.FriendshipStatus toDomainStatus(String rawStatus) {
        String normalized = normalizeStatus(rawStatus);
        if (normalized == null) {
            return com.bif.app.domain.model.FriendshipStatus.PENDING;
        }
        try {
            return com.bif.app.domain.model.FriendshipStatus.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return com.bif.app.domain.model.FriendshipStatus.PENDING;
        }
    }

    private static String normalizeStatus(String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        return rawStatus.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static long parseInstantToMillis(String rawInstant) {
        if (rawInstant == null || rawInstant.trim().isEmpty()) {
            return 0L;
        }
        try {
            return Instant.parse(rawInstant.trim()).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static int stableId(String source) {
        if (source == null || source.trim().isEmpty()) {
            return 0;
        }
        return Math.abs(source.trim().hashCode());
    }
}