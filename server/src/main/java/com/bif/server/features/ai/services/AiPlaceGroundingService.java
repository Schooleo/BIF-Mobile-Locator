package com.bif.server.features.ai.services;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.services.MongoPlaceSearchProvider;
import com.bif.server.features.search.services.TypesensePlaceSearchProvider;

@Service
public class AiPlaceGroundingService {

    private static final int MAX_RESULTS = 8;

    private final String provider;
    private final MongoPlaceSearchProvider mongoPlaceSearchProvider;
    private final TypesensePlaceSearchProvider typesensePlaceSearchProvider;
    private final PlaceRepository placeRepository;
    private final VibeHintNormalizer vibeHintNormalizer;

    public AiPlaceGroundingService(
            @Value("${place.search.provider:mongo}") String provider,
            MongoPlaceSearchProvider mongoPlaceSearchProvider,
            TypesensePlaceSearchProvider typesensePlaceSearchProvider,
            PlaceRepository placeRepository,
            VibeHintNormalizer vibeHintNormalizer) {
        this.provider = provider;
        this.mongoPlaceSearchProvider = mongoPlaceSearchProvider;
        this.typesensePlaceSearchProvider = typesensePlaceSearchProvider;
        this.placeRepository = placeRepository;
        this.vibeHintNormalizer = vibeHintNormalizer;
    }

    public List<Place> ground(PlaceSearchExtraction extraction) {
        if (extraction == null) {
            return List.of();
        }

        LinkedHashSet<String> queryTerms = new LinkedHashSet<>(extraction.keywords());
        if (extraction.category() != null) {
            queryTerms.add(extraction.category());
        }
        queryTerms.addAll(vibeHintNormalizer.expand(extraction.vibe()));
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, Place> matches = new LinkedHashMap<>();
        if (useTypesense()) {
            String combinedQuery = String.join(" ", queryTerms);
            addAll(matches, typesensePlaceSearchProvider.search(
                    combinedQuery,
                    "name,address,tags",
                    MAX_RESULTS));
            if (matches.size() < MAX_RESULTS && extraction.category() != null) {
                addAll(matches, typesensePlaceSearchProvider.search(
                        extraction.category(),
                        "tags,name,address",
                        MAX_RESULTS));
            }
        } else {
            String combinedQuery = String.join(" ", queryTerms);
            addAll(matches, mongoPlaceSearchProvider.search(combinedQuery));
            for (String keyword : extraction.keywords()) {
                if (matches.size() >= MAX_RESULTS) {
                    break;
                }
                addAll(matches, mongoPlaceSearchProvider.search(keyword));
            }
            addTagMatches(matches, extraction.category());
            for (String vibeHint : vibeHintNormalizer.expand(extraction.vibe())) {
                addTagMatches(matches, vibeHint);
            }
        }

        return matches.values().stream()
                .filter(place -> !place.isDeleted())
                .limit(MAX_RESULTS)
                .toList();
    }

    private void addTagMatches(Map<String, Place> matches, String tag) {
        if (tag == null || matches.size() >= MAX_RESULTS) {
            return;
        }
        addAll(matches, placeRepository.findByTagsContaining(tag));
    }

    private void addAll(Map<String, Place> matches, List<Place> candidates) {
        if (candidates == null) {
            return;
        }
        for (Place candidate : candidates) {
            if (candidate == null || candidate.getId() == null || candidate.getId().isBlank()) {
                continue;
            }
            matches.putIfAbsent(candidate.getId(), candidate);
            if (matches.size() >= MAX_RESULTS) {
                return;
            }
        }
    }

    private boolean useTypesense() {
        return provider != null
                && "typesense".equals(provider.trim().toLowerCase(Locale.ROOT));
    }
}
