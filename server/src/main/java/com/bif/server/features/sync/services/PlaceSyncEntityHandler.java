package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class PlaceSyncEntityHandler implements SyncEntityHandler {

    private final PlaceRepository placeRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlaceSyncEntityHandler(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    public String entityType() {
        return "place";
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId,
                                    long newVersion) {
        PlacePayload payload = parsePlacePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        if ("DELETE".equals(operation)) {
            String targetId = pushed.getEntityId();
            if ((targetId == null || targetId.isBlank()) && payload != null) {
                targetId = payload.id;
            }
            if (targetId == null || targetId.isBlank()) {
                return pushed.getPayload();
            }

            final String finalTargetId = targetId;
            Place place = placeRepository.findById(finalTargetId)
                    .orElseGet(() -> {
                        Place tombstone = new Place();
                        tombstone.setId(finalTargetId);
                        return tombstone;
                    });
            place.setDeleted(true);
            place.setServerVersion(newVersion);
            place.setLastModifiedBy(userId);
            placeRepository.save(place);

            PlacePayload responsePayload = toPayload(place);
            responsePayload.deleted = true;
            responsePayload.serverVersion = newVersion;
            return writePayload(responsePayload);
        }

        if (payload == null || payload.id == null || payload.id.isBlank()) {
            return pushed.getPayload();
        }

        Place place = placeRepository.findById(payload.id).orElse(null);
        boolean isDuplicate = false;

        if (place == null) {
            List<Place> existingPlaces = placeRepository.findByNameAndLocationLatitudeAndLocationLongitude(
                    payload.name, payload.latitude, payload.longitude);
            if (!existingPlaces.isEmpty()) {
                place = existingPlaces.get(0);
                isDuplicate = true;
            } else {
                place = new Place();
                place.setId(payload.id);
            }
        }

        place.setName(payload.name);
        place.setAddress(payload.address);
        place.setRating(payload.rating);
        place.setLocation(new Location(payload.latitude, payload.longitude));
        place.setTags(payload.tags);
        place.setPlaceSource(payload.placeSource);
        place.setPersistedByAction(payload.persistedByAction);
        place.setPersistedByUserId(userId);
        place.setReviewCount(payload.reviewCount);
        place.setDeleted(payload.deleted);
        place.setServerVersion(newVersion);
        place.setLastModifiedBy(userId);
        placeRepository.save(place);

        if (isDuplicate) {
            PlacePayload responsePayload = new PlacePayload();
            responsePayload.id = payload.id;
            responsePayload.deleted = true;
            responsePayload.serverVersion = newVersion;
            return writePayload(responsePayload);
        }

        PlacePayload responsePayload = toPayload(place);
        responsePayload.serverVersion = newVersion;
        return writePayload(responsePayload);
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        if (entry.getPayload() != null && !entry.getPayload().isBlank()) {
            return entry.getPayload();
        }

        Optional<Place> placeOpt = placeRepository.findById(entry.getEntityId());
        if (placeOpt.isEmpty()) {
            return null;
        }

        PlacePayload payload = toPayload(placeOpt.get());
        payload.serverVersion = entry.getServerVersion();
        payload.deleted = placeOpt.get().isDeleted();
        return writePayload(payload);
    }

    private PlacePayload parsePlacePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PlacePayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(PlacePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private PlacePayload toPayload(Place place) {
        PlacePayload payload = new PlacePayload();
        payload.id = place.getId();
        payload.name = place.getName();
        payload.address = place.getAddress();
        payload.rating = place.getRating();
        if (place.getLocation() != null) {
            payload.latitude = place.getLocation().getLatitude();
            payload.longitude = place.getLocation().getLongitude();
        }
        payload.tags = place.getTags();
        payload.placeSource = place.getPlaceSource();
        payload.persistedByAction = place.getPersistedByAction();
        payload.persistedByUserId = place.getPersistedByUserId();
        payload.reviewCount = place.getReviewCount();
        payload.serverVersion = place.getServerVersion();
        payload.deleted = place.isDeleted();
        return payload;
    }

    private static class PlacePayload {
        public String id;
        public String name;
        public String address;
        public double rating;
        public double latitude;
        public double longitude;
        public List<String> tags;
        public String placeSource;
        public String persistedByAction;
        public String persistedByUserId;
        public int reviewCount;
        public long serverVersion;
        public boolean deleted;
    }
}
