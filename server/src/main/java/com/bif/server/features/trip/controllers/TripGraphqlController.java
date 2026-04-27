package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.RearrangeStopInput;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.services.TripService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import java.security.Principal;

import java.util.List;

@Controller
public class TripGraphqlController {
    private final TripService tripService;

    public TripGraphqlController(TripService tripService) {
        this.tripService = tripService;
    }

    @QueryMapping
    public List<TripPlan> tripPlans() {
        return tripService.getAll();
    }

    @QueryMapping
    public TripPlan tripPlan(@Argument String id) {
        return tripService.getById(id).orElse(null);
    }

    @QueryMapping
    public List<TripPlan> tripPlansByGroup(@Argument String groupId) {
        return tripService.getByGroupId(groupId);
    }

    @MutationMapping
    public TripPlan upsertTripPlan(@Argument TripPlan input) {
        return tripService.save(input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public TripPlan addTripStop(@Argument String tripId,
                                @Argument TripStop input,
                                Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        return tripService.addStop(tripId, userId, input).orElse(null);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public TripPlan removeTripStop(@Argument String tripId,
                                   @Argument String stopId,
                                   Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        return tripService.removeStop(tripId, userId, stopId).orElse(null);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public TripPlan rearrangeTripStops(@Argument String tripId,
                                       @Argument List<RearrangeStopInput> stops,
                                       Principal principal) {
        String userId = principal != null ? principal.getName() : null;
        return tripService.rearrangeStops(tripId, userId, stops).orElse(null);
    }

    @MutationMapping
    public Boolean deleteTripPlan(@Argument String id) {
        return tripService.deleteById(id);
    }
}

