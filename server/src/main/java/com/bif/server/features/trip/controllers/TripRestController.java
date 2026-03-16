package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.services.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripRestController {
    private final TripService tripService;

    public TripRestController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public List<TripPlan> getTripPlans() {
        return tripService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripPlan> getTripPlanById(@PathVariable String id) {
        return tripService.getById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/group/{groupId}")
    public List<TripPlan> getTripPlansByGroup(@PathVariable String groupId) {
        return tripService.getByGroupId(groupId);
    }

    @PostMapping
    public TripPlan upsertTripPlan(@RequestBody TripPlan tripPlan) {
        return tripService.save(tripPlan);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTripPlan(@PathVariable String id) {
        return tripService.deleteById(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
