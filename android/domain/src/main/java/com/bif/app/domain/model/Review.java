package com.bif.app.domain.model;

public class Review {
    public String placeId;
    public String userId;
    public String userName;
    public int stars;
    public String comment;
    public long createdAt;

    // Place identity metadata for review/place linkage.
    public String externalSource;
    public String externalId;
    public Double lat;
    public Double lng;
    public String placeName;

    // Sync Metadata
    public long serverVersion;
    public boolean deleted;
    public long lastSyncedAt;
    public boolean pendingSync;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Review review = (Review) o;
        return stars == review.stars &&
                createdAt == review.createdAt &&
                serverVersion == review.serverVersion &&
                deleted == review.deleted &&
                lastSyncedAt == review.lastSyncedAt &&
                pendingSync == review.pendingSync &&
                java.util.Objects.equals(placeId, review.placeId) &&
                java.util.Objects.equals(userId, review.userId) &&
                java.util.Objects.equals(userName, review.userName) &&
                java.util.Objects.equals(comment, review.comment) &&
                java.util.Objects.equals(externalSource, review.externalSource) &&
                java.util.Objects.equals(externalId, review.externalId) &&
                java.util.Objects.equals(lat, review.lat) &&
                java.util.Objects.equals(lng, review.lng) &&
                java.util.Objects.equals(placeName, review.placeName);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(
            placeId,
            userId,
            userName,
            stars,
            comment,
            createdAt,
            externalSource,
            externalId,
            lat,
            lng,
            placeName,
            serverVersion,
            deleted,
            lastSyncedAt,
            pendingSync);
    }
}
