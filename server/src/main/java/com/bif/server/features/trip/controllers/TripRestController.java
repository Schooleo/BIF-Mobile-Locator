package com.bif.server.features.trip.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.security.Principal;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.RearrangeStopInput;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.services.TripActivityService;
import com.bif.server.features.trip.services.TripService;

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
            Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        return tripService.addStop(tripId, userId, stop)
                .map(plan -> {
                    tripActivityService.postStopAdded(plan, stop, userId);
                    return ResponseEntity.ok(plan);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{tripId}/stops/{stopId}")
    public ResponseEntity<TripPlan> removeStop(
            @PathVariable String tripId,
            @PathVariable String stopId,
            Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        return tripService.removeStop(tripId, userId, stopId)
                .map(plan -> {
                    tripActivityService.postStopRemoved(plan, stopId, userId);
                    return ResponseEntity.ok(plan);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{tripId}/stops/reorder")
    public ResponseEntity<TripPlan> rearrangeStops(
            @PathVariable String tripId,
            @RequestBody List<TripStop> stops,
            Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        List<RearrangeStopInput> reorderInputs = new java.util.ArrayList<>();
        if (stops != null) {
            for (TripStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                RearrangeStopInput input = new RearrangeStopInput();
                input.setId(stop.getId());
                input.setOrderIndex(stop.getOrderIndex());
                reorderInputs.add(input);
            }
        }

        return tripService.rearrangeStops(tripId, userId, reorderInputs)
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


