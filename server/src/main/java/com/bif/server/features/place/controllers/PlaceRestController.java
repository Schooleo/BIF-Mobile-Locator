package com.bif.server.features.place.controllers;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.services.PlaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceRestController {
    private final PlaceService placeService;

    public PlaceRestController(PlaceService placeService) {
        this.placeService = placeService;
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

    @GetMapping("/search")
    public List<Place> searchPlaces(@RequestParam String q) {
        return placeService.search(q);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlace(@PathVariable String id) {
        return placeService.deleteById(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
