package com.bif.server.features.place.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceMapping;
import com.bif.server.features.place.repositories.PlaceMappingRepository;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import com.bif.server.features.search.services.PlaceSearchProvider;
import com.bif.server.features.sync.services.SyncVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PlaceService {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlaceService.class);
    private static final double MIN_PROJECT_LAT = 8.0d;
    private static final double MAX_PROJECT_LAT = 24.0d;
    private static final double MIN_PROJECT_LNG = 102.0d;
    private static final double MAX_PROJECT_LNG = 110.0d;
    private static final double DEDUP_DISTANCE_METERS = 50.0d;

    private final PlaceRepository placeRepository;
    private final SyncVersionService syncVersionService;
    private final PlaceAddressEnrichmentService placeAddressEnrichmentService;
    private final PlaceIdentityService placeIdentityService;
    private final PlaceMappingRepository placeMappingRepository;
    private final PlaceSearchProvider placeSearchProvider;
    private final PlaceSearchIndexSyncService placeSearchIndexSyncService;
    private final String defaultSearchPlaceSource;

    public PlaceService(PlaceRepository placeRepository,
                        SyncVersionService syncVersionService,
                        PlaceAddressEnrichmentService placeAddressEnrichmentService,
                        PlaceIdentityService placeIdentityService,
                        PlaceMappingRepository placeMappingRepository,
                        PlaceSearchProvider placeSearchProvider,
                        PlaceSearchIndexSyncService placeSearchIndexSyncService,
                        @Value("${place.search.default-source:osm_geocoder}")
                        String defaultSearchPlaceSource) {
        this.placeRepository = placeRepository;
        this.syncVersionService = syncVersionService;
        this.placeAddressEnrichmentService = placeAddressEnrichmentService;
        this.placeIdentityService = placeIdentityService;
        this.placeMappingRepository = placeMappingRepository;
        this.placeSearchProvider = placeSearchProvider;
        this.placeSearchIndexSyncService = placeSearchIndexSyncService;
        this.defaultSearchPlaceSource = defaultSearchPlaceSource;
    }

    public List<Place> getAll() {
        return placeRepository.findAll();
    }

    public Optional<Place> getById(String id) {
        return placeRepository.findById(id);
    }

    public Place save(Place place) {
        validateProjectLocation(place);

        String externalSource = normalizeText(place != null ? place.getPlaceSource() : null);
        String externalId = normalizeText(place != null ? place.getId() : null);
        String resolvedCanonicalId = resolveCanonicalIdForSearch(place, externalSource, externalId);
        if (resolvedCanonicalId != null) {
            place.setId(resolvedCanonicalId);
        }

        Optional<Place> existing = Optional.empty();
        String targetPlaceId = normalizeText(place != null ? place.getId() : null);
        if (targetPlaceId != null) {
            existing = placeRepository.findById(targetPlaceId);
            existing.ifPresent(existingPlace -> mergePlaceForUpsert(existingPlace, place));
        }

        enrichPlaceAddress(place);
        place.setServerVersion(syncVersionService.nextVersion());
        place.setLastModifiedBy(resolveLastModifiedBy(place, existing.orElse(null)));
        Place saved = placeRepository.save(place);
        upsertMappingIfPossible(saved, externalSource, externalId);
        placeSearchIndexSyncService.upsert(saved);
        return saved;
    }

    private void mergePlaceForUpsert(Place existing, Place incoming) {
        if (existing == null || incoming == null) {
            return;
        }

        if (normalizeText(incoming.getName()) == null) {
            incoming.setName(existing.getName());
        }
        if (incoming.getLocation() == null) {
            incoming.setLocation(existing.getLocation());
        }
        if (normalizeText(incoming.getAddress()) == null) {
            incoming.setAddress(existing.getAddress());
        }
        if (normalizeText(incoming.getCountry()) == null) {
            incoming.setCountry(existing.getCountry());
        }
        if (normalizeText(incoming.getRegion()) == null) {
            incoming.setRegion(existing.getRegion());
        }
        if (normalizeText(incoming.getLocality()) == null) {
            incoming.setLocality(existing.getLocality());
        }
        if (normalizeText(incoming.getCity()) == null) {
            incoming.setCity(existing.getCity());
        }
        if (normalizeText(incoming.getDistrict()) == null) {
            incoming.setDistrict(existing.getDistrict());
        }
        if (incoming.getTags() == null || incoming.getTags().isEmpty()) {
            incoming.setTags(existing.getTags());
        }
        if (normalizeText(incoming.getCategoryMain()) == null) {
            incoming.setCategoryMain(existing.getCategoryMain());
        }
        if (incoming.getCategoryAlternates() == null || incoming.getCategoryAlternates().isEmpty()) {
            incoming.setCategoryAlternates(existing.getCategoryAlternates());
        }
        if (normalizeText(incoming.getNameNormalized()) == null) {
            incoming.setNameNormalized(existing.getNameNormalized());
        }
        if (normalizeText(incoming.getAddressNormalized()) == null) {
            incoming.setAddressNormalized(existing.getAddressNormalized());
        }
        if (normalizeText(incoming.getPlaceSource()) == null) {
            incoming.setPlaceSource(existing.getPlaceSource());
        }
        if (normalizeText(incoming.getPersistedByAction()) == null) {
            incoming.setPersistedByAction(existing.getPersistedByAction());
        }
        if (normalizeText(incoming.getPersistedByUserId()) == null) {
            incoming.setPersistedByUserId(existing.getPersistedByUserId());
        }
        if (Double.compare(incoming.getRating(), 0.0d) == 0 && Double.compare(existing.getRating(), 0.0d) != 0) {
            incoming.setRating(existing.getRating());
        }
        if (incoming.getReviewCount() == 0 && existing.getReviewCount() != 0) {
            incoming.setReviewCount(existing.getReviewCount());
        }
        if (normalizeText(incoming.getPhotoUrl()) == null) {
            incoming.setPhotoUrl(existing.getPhotoUrl());
        }
        if (existing.isDeleted() && !incoming.isDeleted()) {
            incoming.setDeleted(true);
        }
        if (existing.isOrphaned() && !incoming.isOrphaned()) {
            incoming.setOrphaned(true);
        }
        if (incoming.getOrphanedAt() == null && existing.getOrphanedAt() != null) {
            incoming.setOrphanedAt(existing.getOrphanedAt());
        }
    }

    private String resolveLastModifiedBy(Place place, Place existing) {
        String persistedByUserId = normalizeText(place != null ? place.getPersistedByUserId() : null);
        if (persistedByUserId != null) {
            return persistedByUserId;
        }

        String explicitLastModifiedBy = normalizeText(place != null ? place.getLastModifiedBy() : null);
        if (explicitLastModifiedBy != null) {
            return explicitLastModifiedBy;
        }

        String existingLastModifiedBy = normalizeText(existing != null ? existing.getLastModifiedBy() : null);
        if (existingLastModifiedBy != null) {
            return existingLastModifiedBy;
        }

        String existingUserId = normalizeText(existing != null ? existing.getPersistedByUserId() : null);
        if (existingUserId != null) {
            return existingUserId;
        }

        return "system";
    }

    public Place saveFromSearch(Place place) {
        validateProjectLocation(place);

        if (place != null && (place.getPlaceSource() == null || place.getPlaceSource().isBlank())) {
            place.setPlaceSource(defaultSearchPlaceSource);
        }

        String externalSource = normalizeText(place != null ? place.getPlaceSource() : null);
        String externalId = normalizeText(place != null ? place.getId() : null);

        if (isGeocodeExternalId(externalId)) {
            Optional<Place> duplicate = findNearDuplicate(place);
            if (duplicate.isPresent()) {
                upsertMappingIfPossible(duplicate.get(), externalSource, externalId);
                return duplicate.get();
            }
        }

        String resolvedCanonicalId = resolveCanonicalIdForSearch(place, externalSource, externalId);
        if (resolvedCanonicalId != null) {
            place.setId(resolvedCanonicalId);
        }

        Optional<Place> existing = placeRepository.findById(place.getId());
        if (existing.isPresent()) {
            upsertMappingIfPossible(existing.get(), externalSource, externalId);
            return existing.get();
        }

        enrichPlaceAddress(place);
        place.setPersistedByAction("search_discovered");
        place.setServerVersion(syncVersionService.nextVersion());
        Place saved = placeRepository.save(place);
        upsertMappingIfPossible(saved, externalSource, externalId);
        placeSearchIndexSyncService.upsert(saved);
        return saved;
    }

    private Optional<Place> findNearDuplicate(Place candidate) {
        if (candidate == null || candidate.getName() == null || candidate.getLocation() == null) {
            return Optional.empty();
        }

        String normalizedCandidateName = candidate.getName().trim().toLowerCase(Locale.ROOT);
        if (normalizedCandidateName.isEmpty()) {
            return Optional.empty();
        }

        List<Place> maybeSameName = placeRepository
                .findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                        candidate.getName(),
                        candidate.getName());

        for (Place existing : maybeSameName) {
            if (existing == null || existing.getLocation() == null || existing.getName() == null) {
                continue;
            }

            String normalizedExistingName = existing.getName().trim().toLowerCase(Locale.ROOT);
            if (!normalizedCandidateName.equals(normalizedExistingName)) {
                continue;
            }

            double meters = distanceMeters(candidate, existing);
            if (meters < DEDUP_DISTANCE_METERS) {
                return Optional.of(existing);
            }
        }

        return Optional.empty();
    }

    private double distanceMeters(Place from, Place to) {
        double lat1 = from.getLocation().getLatitude();
        double lng1 = from.getLocation().getLongitude();
        double lat2 = to.getLocation().getLatitude();
        double lng2 = to.getLocation().getLongitude();

        double earthRadiusMeters = 6371000.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);

        double sinHalfLat = Math.sin(dLat / 2.0d);
        double sinHalfLng = Math.sin(dLng / 2.0d);
        double a = sinHalfLat * sinHalfLat
                + Math.cos(latRad1) * Math.cos(latRad2)
                * sinHalfLng * sinHalfLng;
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return earthRadiusMeters * c;
    }

    private String resolveCanonicalIdForSearch(Place place,
                                               String externalSource,
                                               String externalId) {
        if (place == null || externalSource == null || externalId == null) {
            return null;
        }
        if (place.getLocation() == null || !Double.isFinite(place.getLocation().getLatitude())
                || !Double.isFinite(place.getLocation().getLongitude())) {
            return null;
        }
        String name = normalizeText(place.getName());
        if (name == null) {
            return null;
        }

        try {
            return placeIdentityService.resolveInternalPlaceId(
                    externalSource,
                    externalId,
                    place.getLocation().getLatitude(),
                    place.getLocation().getLongitude(),
                    name);
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to resolve canonical placeId for search place source={}, extId={}",
                    externalSource,
                    externalId,
                    ex);
            return null;
        }
    }

    private void upsertMappingIfPossible(Place canonicalPlace,
                                         String externalSource,
                                         String externalId) {
        if (canonicalPlace == null || canonicalPlace.getLocation() == null
                || externalSource == null || externalId == null) {
            return;
        }

        Location location = canonicalPlace.getLocation();
        if (!Double.isFinite(location.getLatitude()) || !Double.isFinite(location.getLongitude())) {
            return;
        }

        String placeName = normalizeText(canonicalPlace.getName());
        if (placeName == null) {
            return;
        }

        PlaceMapping mapping = placeMappingRepository.upsertByExternalKey(
                externalSource,
                externalId,
                canonicalPlace.getId(),
                placeName,
                location.getLatitude(),
                location.getLongitude());

        if (mapping == null) {
            placeMappingRepository.findByExternalSourceAndExternalId(externalSource, externalId)
                    .ifPresent(existing -> LOGGER.debug(
                            "Mapping already exists for source={} externalId={} -> internalId={}",
                            externalSource,
                            externalId,
                            existing.getInternalPlaceId()));
        }
    }

    private boolean isGeocodeExternalId(String externalId) {
        return externalId != null && externalId.startsWith("geocode_");
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateProjectLocation(Place place) {
        if (place == null || place.getLocation() == null) {
            throw new IllegalArgumentException("Missing or invalid place location");
        }

        double latitude = place.getLocation().getLatitude();
        double longitude = place.getLocation().getLongitude();

        boolean valid = Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && Double.compare(latitude, 0.0d) != 0
                && Double.compare(longitude, 0.0d) != 0
                && latitude >= MIN_PROJECT_LAT
                && latitude <= MAX_PROJECT_LAT
                && longitude >= MIN_PROJECT_LNG
                && longitude <= MAX_PROJECT_LNG;

        if (!valid) {
            throw new IllegalArgumentException("Invalid place coordinates for project bounds");
        }
    }

    private void enrichPlaceAddress(Place place) {
        if (place == null) {
            return;
        }

        Double latitude = null;
        Double longitude = null;
        if (place.getLocation() != null) {
            latitude = place.getLocation().getLatitude();
            longitude = place.getLocation().getLongitude();
        }

        place.setAddress(placeAddressEnrichmentService.enrichAddress(
                place.getAddress(), latitude, longitude));
    }

    public boolean deleteById(String id) {
        return placeRepository.findById(id).map(place -> {
            place.setDeleted(true);
            place.setServerVersion(syncVersionService.nextVersion());
            placeRepository.save(place);
            placeSearchIndexSyncService.deleteById(id);
            return true;
        }).orElse(false);
    }

    public List<Place> search(PlaceSearchRequestDTO request) {
        return placeSearchProvider.search(request);
    }

    public List<Place> search(String query) {
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
        request.setQuery(query);
        return search(request);
    }

    public List<Place> getByTag(String tag) {
        return placeRepository.findByTagsContaining(tag);
    }

    public List<Place> getByUserId(String userId) {
        return placeRepository.findByPersistedByUserId(userId);
    }
}
