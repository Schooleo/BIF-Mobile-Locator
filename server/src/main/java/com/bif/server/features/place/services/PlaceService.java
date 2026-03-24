package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.models.PlaceReview;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.sync.services.SyncVersionService;
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

    public PlaceService(PlaceRepository placeRepository,
                        SyncVersionService syncVersionService) {
        this.placeRepository = placeRepository;
        this.syncVersionService = syncVersionService;
    }

    public List<Place> getAll() {
        return placeRepository.findAll();
    }

    public Optional<Place> getById(String id) {
        return placeRepository.findById(id);
    }

    public Place save(Place place) {
        place.setServerVersion(syncVersionService.nextVersion());
        place.setLastModifiedBy(place.getPersistedByUserId());
        return placeRepository.save(place);
    }

    public Place saveFromSearch(Place place) {
        return placeRepository.findById(place.getId()).orElseGet(() -> {
            place.setPlaceSource("google_maps");
            place.setPersistedByAction("search_discovered");
            place.setServerVersion(syncVersionService.nextVersion());
            return placeRepository.save(place);
        });
    }

    public boolean deleteById(String id) {
        return placeRepository.findById(id).map(place -> {
            place.setDeleted(true);
            place.setServerVersion(syncVersionService.nextVersion());
            placeRepository.save(place);
            return true;
        }).orElse(false);
    }

    public List<Place> search(String query) {
        return placeRepository
                .findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                        query, query);
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
            return placeRepository.save(place);
        }).orElseThrow(() -> new NoSuchElementException(
                "Place not found: " + placeId));
    }
}
