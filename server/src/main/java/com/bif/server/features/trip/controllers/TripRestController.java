package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.services.TripActivityService;
import com.bif.server.features.trip.services.TripService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripRestController {
    private final TripService tripService;
    private final TripActivityService tripActivityService;

    public TripRestController(TripService tripService,
                              TripActivityService tripActivityService) {
        this.tripService = tripService;
        this.tripActivityService = tripActivityService;
    }

    @GetMapping
    public List<TripPlan> getTripPlans() {
        return tripService.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripPlan> getTripPlanById(@PathVariable String id) {
        return tripService.getById(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/group/{groupId}")
    public List<TripPlan> getTripPlansByGroup(@PathVariable String groupId) {
        return tripService.getByGroupId(groupId);
    }

    @PostMapping
    public TripPlan upsertTripPlan(@RequestBody TripPlan tripPlan,
                                   @RequestParam(required = false) String userId) {
        boolean isNew = tripPlan.getId() == null;
        TripPlan saved = tripService.save(tripPlan);
        if (isNew) {
            tripActivityService.postTripCreated(saved, userId);
        } else {
            tripActivityService.postTripUpdated(saved, userId);
        }
        return saved;
    }

    @PostMapping("/{tripId}/stops")
    public ResponseEntity<TripPlan> addStop(
            @PathVariable String tripId,
            @RequestBody TripStop stop,
            @RequestParam(required = false) String userId) {
        return tripService.addStop(tripId, stop)
                .map(plan -> {
                    tripActivityService.postStopAdded(plan, stop, userId);
                    return ResponseEntity.ok(plan);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tripId}/stops/{orderIndex}")
    public ResponseEntity<TripPlan> removeStop(
            @PathVariable String tripId,
            @PathVariable int orderIndex,
            @RequestParam(required = false) String userId) {
        return tripService.removeStop(tripId, orderIndex)
                .map(plan -> {
                    tripActivityService.postStopRemoved(plan, orderIndex, userId);
                    return ResponseEntity.ok(plan);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{tripId}/stops/reorder")
    public ResponseEntity<TripPlan> rearrangeStops(
            @PathVariable String tripId,
            @RequestBody List<TripStop> stops,
            @RequestParam(required = false) String userId) {
        return tripService.rearrangeStops(tripId, stops)
                .map(plan -> {
                    tripActivityService.postStopsRearranged(plan, userId);
                    return ResponseEntity.ok(plan);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTripPlan(
            @PathVariable String id,
            @RequestParam(required = false) String userId) {
        return tripService.getById(id)
                .map(plan -> {
                    tripService.deleteById(id);
                    tripActivityService.postTripDeleted(
                            plan.getGroupId(), plan.getTitle(), userId);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

