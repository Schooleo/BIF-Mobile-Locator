package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
@Primary
public class ConfigurablePlaceSearchProvider implements PlaceSearchProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            ConfigurablePlaceSearchProvider.class);

    private final String provider;
    private final MongoPlaceSearchProvider mongoPlaceSearchProvider;
    private final TypesensePlaceSearchProvider typesensePlaceSearchProvider;

    public ConfigurablePlaceSearchProvider(
            @Value("${place.search.provider:mongo}") String provider,
            MongoPlaceSearchProvider mongoPlaceSearchProvider,
            TypesensePlaceSearchProvider typesensePlaceSearchProvider) {
        this.provider = provider;
        this.mongoPlaceSearchProvider = mongoPlaceSearchProvider;
        this.typesensePlaceSearchProvider = typesensePlaceSearchProvider;
    }

    @Override
    public List<Place> search(PlaceSearchRequestDTO request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return Collections.emptyList();
        }

        String resolvedProvider = provider == null
                ? "mongo"
                : provider.trim().toLowerCase(Locale.ROOT);

        if ("typesense".equals(resolvedProvider)) {
            return typesensePlaceSearchProvider.search(request);
        }

        if (!"mongo".equals(resolvedProvider)) {
            LOGGER.warn("Unknown place.search.provider='{}'; falling back to mongo", provider);
        }
        return mongoPlaceSearchProvider.search(request);
    }

    public List<Place> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
        request.setQuery(query);
        return search(request);
    }
}
