package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceMapping;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.place.services.PlaceAddressEnrichmentService;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Collections;

@Component
public class PlaceSyncEntityHandler implements SyncEntityHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceSyncEntityHandler.class);

    private final PlaceRepository placeRepository;
    private final PlaceMappingRepository placeMappingRepository;
    private final PlaceAddressEnrichmentService placeAddressEnrichmentService;
    private final PlaceIdentityService placeIdentityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlaceSyncEntityHandler(
            PlaceRepository placeRepository,
            PlaceMappingRepository placeMappingRepository,
            PlaceAddressEnrichmentService placeAddressEnrichmentService,
            PlaceIdentityService placeIdentityService) {
        this.placeRepository = placeRepository;
        this.placeMappingRepository = placeMappingRepository;
        this.placeAddressEnrichmentService = placeAddressEnrichmentService;
        this.placeIdentityService = placeIdentityService;
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

        String canonicalPlaceId = resolveCanonicalPlaceId(payload);
        String targetPlaceId = canonicalPlaceId != null ? canonicalPlaceId : payload.id.trim();

        Place place = placeRepository.findById(targetPlaceId).orElseGet(() -> {
            Place created = new Place();
            created.setId(targetPlaceId);
            return created;
        });

        place.setName(payload.name);
        Double latitude = hasUsableLocation(payload) ? payload.latitude : null;
        Double longitude = hasUsableLocation(payload) ? payload.longitude : null;
        place.setAddress(placeAddressEnrichmentService.enrichAddress(
            payload.address, latitude, longitude));
        place.setRating(payload.rating);
        if (latitude != null && longitude != null) {
            place.setLocation(new Location(latitude, longitude));
        } else if (place.getLocation() == null) {
            place.setLocation(null);
        }
        place.setTags(payload.tags);
        if (!isBlank(payload.placeSource)) {
            place.setPlaceSource(payload.placeSource.trim());
        }
        place.setPersistedByAction(payload.persistedByAction);
        place.setPersistedByUserId(userId);
        place.setReviewCount(payload.reviewCount);
        place.setPhotoUrl(payload.photoUrl);
        place.setDeleted(payload.deleted);
        place.setServerVersion(newVersion);
        place.setLastModifiedBy(userId);
        placeRepository.save(place);

        if (!isBlank(canonicalPlaceId) && !isBlank(place.getId())) {
            upsertMappingIfPossible(payload, place);
        }

        if (!targetPlaceId.equals(payload.id.trim())) {
            PlacePayload responsePayload = toPayload(place);
            responsePayload.id = payload.id;
            responsePayload.canonicalId = targetPlaceId;
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
        payload.externalId = resolveExternalIdForPayload(place);
        payload.persistedByAction = place.getPersistedByAction();
        payload.persistedByUserId = place.getPersistedByUserId();
        payload.reviewCount = place.getReviewCount();
        payload.photoUrl = place.getPhotoUrl();
        payload.serverVersion = place.getServerVersion();
        payload.deleted = place.isDeleted();
        return payload;
    }

    private String resolveExternalIdForPayload(Place place) {
        if (place == null) {
            return null;
        }

        String placeSource = normalizeText(place.getPlaceSource());
        if (placeSource == null) {
            return null;
        }

        String internalPlaceId = normalizeText(place.getId());
        if (internalPlaceId == null) {
            return null;
        }

        List<PlaceMapping> mappings = placeMappingRepository.findByInternalPlaceId(internalPlaceId);
        if (mappings == null) {
            mappings = Collections.emptyList();
        }

        return mappings
                .stream()
                .filter(mapping -> placeSource.equalsIgnoreCase(normalizeText(mapping.getExternalSource())))
                .map(PlaceMapping::getExternalId)
                .map(this::normalizeText)
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private String resolveCanonicalPlaceId(PlacePayload payload) {
        if (payload == null || isBlank(payload.placeSource) || isBlank(payload.name) || !hasUsableLocation(payload)) {
            return null;
        }

        String externalId = normalizeText(payload.externalId);
        if (externalId == null) {
            externalId = normalizeText(payload.id);
        }
        if (externalId == null) {
            return null;
        }

        try {
            return placeIdentityService.resolveInternalPlaceId(
                    payload.placeSource.trim(),
                    externalId,
                    payload.latitude,
                    payload.longitude,
                    payload.name.trim());
        } catch (RuntimeException ex) {
            LOGGER.warn("Place sync: failed to resolve canonical placeId for source={} externalId={} entityId={}",
                    payload.placeSource,
                    externalId,
                    payload.id,
                    ex);
            return null;
        }
    }

    private void upsertMappingIfPossible(PlacePayload payload, Place canonicalPlace) {
        if (payload == null || canonicalPlace == null || canonicalPlace.getLocation() == null || isBlank(payload.placeSource)) {
            return;
        }

        String externalId = normalizeText(payload.externalId);
        if (externalId == null) {
            externalId = normalizeText(payload.id);
        }
        if (externalId == null) {
            return;
        }

        String placeName = normalizeText(canonicalPlace.getName());
        if (placeName == null) {
            return;
        }

        final String externalSource = payload.placeSource.trim();
        final String resolvedExternalId = externalId;

        PlaceMapping mapping = placeMappingRepository.upsertByExternalKey(
            externalSource,
            resolvedExternalId,
                canonicalPlace.getId(),
                placeName,
                canonicalPlace.getLocation().getLatitude(),
                canonicalPlace.getLocation().getLongitude());

        if (mapping == null) {
            placeMappingRepository.findByExternalSourceAndExternalId(externalSource, resolvedExternalId)
                    .ifPresent(existing -> LOGGER.debug(
                            "Place sync mapping already exists for source={} externalId={} -> internalId={}",
                    externalSource,
                    resolvedExternalId,
                            existing.getInternalPlaceId()));
        }
    }

    private boolean hasUsableLocation(PlacePayload payload) {
        if (payload == null || !Double.isFinite(payload.latitude) || !Double.isFinite(payload.longitude)) {
            return false;
        }
        return !(Double.compare(payload.latitude, 0.0d) == 0 && Double.compare(payload.longitude, 0.0d) == 0);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static class PlacePayload {
        public String id;
        public String name;
        public String address;
        public double rating;
        public double latitude;
        public double longitude;
        public String externalId;
        public String canonicalId;
        public List<String> tags;
        public String placeSource;
        public String persistedByAction;
        public String persistedByUserId;
        public int reviewCount;
        public String photoUrl;
        public long serverVersion;
        public boolean deleted;
    }
}
