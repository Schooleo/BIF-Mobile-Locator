package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@Qualifier("mongoPlaceSearchProvider")
public class MongoPlaceSearchProvider implements PlaceSearchProvider {

    private final PlaceRepository placeRepository;

    public MongoPlaceSearchProvider(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    @Override
    public List<Place> search(PlaceSearchRequestDTO request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return Collections.emptyList();
        }
        String query = request.getQuery();
        List<Place> results = placeRepository.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(
                query, query);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        List<Place> ranked = new ArrayList<>(results);
        if (hasCoordinates(request)) {
            final double originLat = request.getLatitude();
            final double originLng = request.getLongitude();
            ranked.sort(Comparator
                    .comparingDouble((Place place) -> distanceKm(originLat, originLng, place))
                    .thenComparing((left, right) -> Double.compare(right.getRating(), left.getRating()))
                    .thenComparing(place -> safeLower(place != null ? place.getName() : null)));
        }

        int perPage = request.getPerPage();
        if (ranked.size() > perPage) {
            return new ArrayList<>(ranked.subList(0, perPage));
        }
        return ranked;
    }

    public List<Place> search(String query) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
        request.setQuery(query);
        return search(request);
    }

    private boolean hasCoordinates(PlaceSearchRequestDTO request) {
        if (request == null) {
            return false;
        }

        Double latitude = request.getLatitude();
        Double longitude = request.getLongitude();
        if (latitude == null || longitude == null) {
            return false;
        }

        if (!Double.isFinite(latitude)
                || !Double.isFinite(longitude)
                || latitude < -90.0d || latitude > 90.0d
                || longitude < -180.0d || longitude > 180.0d) {
            return false;
        }

        return !(Double.compare(latitude, 0.0d) == 0
                && Double.compare(longitude, 0.0d) == 0);
    }

    private double distanceKm(double originLat, double originLng, Place place) {
        if (place == null
                || place.getLocation() == null
                || !Double.isFinite(place.getLocation().getLatitude())
                || !Double.isFinite(place.getLocation().getLongitude())) {
            return Double.MAX_VALUE;
        }

        double lat = place.getLocation().getLatitude();
        double lng = place.getLocation().getLongitude();
        if (lat < -90.0d || lat > 90.0d || lng < -180.0d || lng > 180.0d) {
            return Double.MAX_VALUE;
        }

        double earthRadiusKm = 6371.0d;
        double dLat = Math.toRadians(lat - originLat);
        double dLng = Math.toRadians(lng - originLng);
        double lat1 = Math.toRadians(originLat);
        double lat2 = Math.toRadians(lat);

        double sinLat = Math.sin(dLat / 2.0d);
        double sinLng = Math.sin(dLng / 2.0d);
        double a = sinLat * sinLat
                + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0d, 1.0d - a)));
        return earthRadiusKm * c;
    }

    private String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }
}
