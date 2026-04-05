package com.bif.server.features.place.controllers;

import com.bif.server.features.place.dto.rest.PlaceResolveRequest;
import com.bif.server.features.place.dto.rest.PlaceResolveResponse;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.place.services.PlaceService;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceRestController {
    private final PlaceService placeService;
    private final PlaceIdentityService placeIdentityService;

    public PlaceRestController(PlaceService placeService, PlaceIdentityService placeIdentityService) {
        this.placeService = placeService;
        this.placeIdentityService = placeIdentityService;
    }

    @GetMapping
    public List<Place> getPlaces() {
        return placeService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Place> getPlaceById(@PathVariable String id) {
        return placeService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/search")
    public List<Place> searchPlaces(@RequestBody PlaceSearchRequestDTO request) {
        return placeService.search(request);
    }

    @GetMapping("/tag/{tag}")
    public List<Place> getByTag(@PathVariable String tag) {
        return placeService.getByTag(tag);
    }

    @GetMapping("/user/{userId}")
    public List<Place> getByUser(@PathVariable String userId) {
        return placeService.getByUserId(userId);
    }

    @PostMapping
    public Place upsertPlace(@RequestBody Place place) {
        return placeService.save(place);
    }

    @PostMapping("/from-search")
    public Place saveFromSearch(@RequestBody Place place) {
        return placeService.saveFromSearch(place);
    }

    @PostMapping("/resolve")
    public ResponseEntity<PlaceResolveResponse> resolvePlace(@RequestBody PlaceResolveRequest request) {
        String internalId = placeIdentityService.resolveInternalPlaceId(
                request.externalSource(),
                request.externalId(),
                request.lat(),
                request.lng(),
                request.name()
        );
        return ResponseEntity.ok(new PlaceResolveResponse(internalId, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlace(@PathVariable String id) {
        return placeService.deleteById(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
