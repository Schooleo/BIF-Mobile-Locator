package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.services.PlaceSearchIndexSyncService;
import com.bif.server.features.search.services.PlaceSearchProvider;
import com.bif.server.features.sync.services.SyncVersionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class PlaceService {
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
        enrichPlaceAddress(place);
        place.setServerVersion(syncVersionService.nextVersion());
        place.setLastModifiedBy(place.getPersistedByUserId());
        Place saved = placeRepository.save(place);
        placeSearchIndexSyncService.upsert(saved);
        return saved;
    }

    public Place saveFromSearch(Place place) {
        return placeRepository.findById(place.getId()).orElseGet(() -> {
            enrichPlaceAddress(place);
            place.setPlaceSource(defaultSearchPlaceSource);
            place.setPersistedByAction("search_discovered");
            place.setServerVersion(syncVersionService.nextVersion());
            Place saved = placeRepository.save(place);
            placeSearchIndexSyncService.upsert(saved);
            return saved;
        });
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

    public List<Place> search(String query) {
        return placeSearchProvider.search(query);
    }

    public List<Place> getByTag(String tag) {
        return placeRepository.findByTagsContaining(tag);
    }

    public List<Place> getByUserId(String userId) {
        return placeRepository.findByPersistedByUserId(userId);
    }

    public Place addReview(String placeId, PlaceReview review) {
        return placeRepository.findById(placeId).map(place -> {
            if (place.getReviews() == null) {
                place.setReviews(new ArrayList<>());
            }
            review.setCreatedAt(Instant.now());
            place.getReviews().add(review);
            place.setReviewCount(place.getReviews().size());
            place.setRating(place.getReviews().stream()
                    .mapToInt(PlaceReview::getRating)
                    .average()
                    .orElse(0));
            place.setServerVersion(syncVersionService.nextVersion());
                Place saved = placeRepository.save(place);
                placeSearchIndexSyncService.upsert(saved);
                return saved;
        }).orElseThrow(() -> new NoSuchElementException(
                "Place not found: " + placeId));
    }
}
