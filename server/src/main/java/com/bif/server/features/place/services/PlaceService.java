package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import com.bif.server.features.search.services.PlaceSearchProvider;
import com.bif.server.features.sync.services.SyncVersionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class PlaceService {
    private static final double MIN_PROJECT_LAT = 8.0d;
    private static final double MAX_PROJECT_LAT = 24.0d;
    private static final double MIN_PROJECT_LNG = 102.0d;
    private static final double MAX_PROJECT_LNG = 110.0d;
    private static final double DEDUP_DISTANCE_METERS = 50.0d;

    private final PlaceRepository placeRepository;
    private final SyncVersionService syncVersionService;
    private final PlaceAddressEnrichmentService placeAddressEnrichmentService;
    private final PlaceSearchProvider placeSearchProvider;
    private final PlaceSearchIndexSyncService placeSearchIndexSyncService;
    private final String defaultSearchPlaceSource;

    public PlaceService(PlaceRepository placeRepository,
                        SyncVersionService syncVersionService,
                        PlaceAddressEnrichmentService placeAddressEnrichmentService,
                        PlaceSearchProvider placeSearchProvider,
                        PlaceSearchIndexSyncService placeSearchIndexSyncService,
                        @Value("${place.search.default-source:osm_geocoder}")
                        String defaultSearchPlaceSource) {
        this.placeRepository = placeRepository;
        this.syncVersionService = syncVersionService;
        this.placeAddressEnrichmentService = placeAddressEnrichmentService;
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
        enrichPlaceAddress(place);
        place.setServerVersion(syncVersionService.nextVersion());
        place.setLastModifiedBy(place.getPersistedByUserId());
        Place saved = placeRepository.save(place);
        placeSearchIndexSyncService.upsert(saved);
        return saved;
    }

    public Place saveFromSearch(Place place) {
        validateProjectLocation(place);

        if (place != null && place.getPlaceSource() == null) {
            place.setPlaceSource(defaultSearchPlaceSource);
        }

        if (isGeocodeId(place)) {
            Optional<Place> duplicate = findNearDuplicate(place);
            if (duplicate.isPresent()) {
                return duplicate.get();
            }
        }

        return placeRepository.findById(place.getId()).orElseGet(() -> {
            enrichPlaceAddress(place);
            if (place.getPlaceSource() == null || place.getPlaceSource().isBlank()) {
                place.setPlaceSource(defaultSearchPlaceSource);
            }
            place.setPersistedByAction("search_discovered");
            place.setServerVersion(syncVersionService.nextVersion());
            Place saved = placeRepository.save(place);
            placeSearchIndexSyncService.upsert(saved);
            return saved;
        });
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

    private boolean isGeocodeId(Place place) {
        return place != null
                && place.getId() != null
                && place.getId().startsWith("geocode_");
    }

    private void validateProjectLocation(Place place) {
        if (place == null || place.getLocation() == null) {
            return;
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
