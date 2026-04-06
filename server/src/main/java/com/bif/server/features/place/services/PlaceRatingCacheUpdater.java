package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.sync.services.SyncVersionService;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.NoSuchElementException;

@Component
public class PlaceRatingCacheUpdater {
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final MongoTemplate mongoTemplate;
    private final SyncVersionService syncVersionService;

    public PlaceRatingCacheUpdater(MongoTemplate mongoTemplate,
                                   SyncVersionService syncVersionService) {
        this.mongoTemplate = mongoTemplate;
        this.syncVersionService = syncVersionService;
    }

    public Place incrementAndRecalculate(String userId, String placeId, int stars) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            Query snapshotQuery = Query.query(
                Criteria.where("_id").is(placeId)
                    .and("deleted").ne(true));
            snapshotQuery.fields().include("reviewCount").include("rating");

            Place snapshot = mongoTemplate.findOne(snapshotQuery, Place.class);
            if (snapshot == null) {
            throw new NoSuchElementException("Place not found: " + placeId);
            }

            int oldCount = Math.max(snapshot.getReviewCount(), 0);
            double oldRating = snapshot.getRating();
            int newCount = oldCount + 1;
            double newRating = ((oldRating * oldCount) + stars) / newCount;

            Query compareAndSetQuery = Query.query(
                Criteria.where("_id").is(placeId)
                    .and("deleted").ne(true)
                    .and("reviewCount").is(oldCount));

            Update update = new Update()
                .inc("reviewCount", 1)
                .set("rating", newRating)
                .set("lastModifiedBy", userId)
                .set("serverVersion", syncVersionService.nextVersion())
                .set("updatedAt", Instant.now());

            Place updated = mongoTemplate.findAndModify(
                compareAndSetQuery,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Place.class);

            if (updated != null) {
            return updated;
            }
        }

        throw new IllegalStateException(
            "Unable to update place rating after " + MAX_RETRY_ATTEMPTS + " attempts");
    }

    public Place decrementAndRecalculate(String userId, String placeId, int deletedStars) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            Query snapshotQuery = Query.query(
                Criteria.where("_id").is(placeId)
                    .and("deleted").ne(true));
            snapshotQuery.fields().include("reviewCount").include("rating");

            Place snapshot = mongoTemplate.findOne(snapshotQuery, Place.class);
            if (snapshot == null) {
                throw new NoSuchElementException("Place not found: " + placeId);
            }

            int oldCount = Math.max(snapshot.getReviewCount(), 0);
            if (oldCount <= 0) {
                throw new IllegalStateException(
                    "Cannot decrement reviewCount below zero for place " + placeId);
            }

            double oldRating = snapshot.getRating();
            int newCount = oldCount - 1;
            double newRating = oldCount > 1
                ? ((oldRating * oldCount) - deletedStars) / newCount
                : 0.0;

            Query compareAndSetQuery = Query.query(
                Criteria.where("_id").is(placeId)
                    .and("deleted").ne(true)
                    .and("reviewCount").is(oldCount));

            Update update = new Update()
                .inc("reviewCount", -1)
                .set("rating", Math.max(newRating, 0.0))
                .set("lastModifiedBy", userId)
                .set("serverVersion", syncVersionService.nextVersion())
                .set("updatedAt", Instant.now());

            Place updated = mongoTemplate.findAndModify(
                compareAndSetQuery,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Place.class);

            if (updated != null) {
                return updated;
            }
        }

        throw new IllegalStateException(
            "Unable to update place rating after " + MAX_RETRY_ATTEMPTS + " attempts");
    }

    public Place replaceAndRecalculate(String userId, String placeId, int oldStars, int newStars) {
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            Query snapshotQuery = Query.query(
                Criteria.where("_id").is(placeId)
                    .and("deleted").ne(true));
            snapshotQuery.fields()
                .include("reviewCount")
                .include("rating")
                .include("serverVersion");

            Place snapshot = mongoTemplate.findOne(snapshotQuery, Place.class);
            if (snapshot == null) {
                throw new NoSuchElementException("Place not found: " + placeId);
            }

            int reviewCount = Math.max(snapshot.getReviewCount(), 0);
            if (reviewCount <= 0) {
                throw new IllegalStateException(
                    "Cannot replace stars when reviewCount is zero for place " + placeId);
            }

            double oldRating = snapshot.getRating();
            double totalStars = (oldRating * reviewCount) - oldStars + newStars;
            double recalculatedRating = Math.max(0.0, Math.min(5.0, totalStars / reviewCount));

            Query compareAndSetQuery = Query.query(
                Criteria.where("_id").is(placeId)
                    .and("deleted").ne(true)
                    .and("reviewCount").is(reviewCount)
                    .and("serverVersion").is(snapshot.getServerVersion()));

            Update update = new Update()
                .set("rating", recalculatedRating)
                .set("lastModifiedBy", userId)
                .set("serverVersion", syncVersionService.nextVersion())
                .set("updatedAt", Instant.now());

            Place updated = mongoTemplate.findAndModify(
                compareAndSetQuery,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Place.class);

            if (updated != null) {
                return updated;
            }
        }

        throw new IllegalStateException(
            "Unable to update place rating after " + MAX_RETRY_ATTEMPTS + " attempts");
    }
}
