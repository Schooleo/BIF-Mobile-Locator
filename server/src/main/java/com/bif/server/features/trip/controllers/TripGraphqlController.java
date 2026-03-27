package com.bif.server.features.trip.controllers;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.services.TripService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

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
    public TripPlan addTripStop(@Argument String tripId,
                                @Argument TripStop input) {
        return tripService.addStop(tripId, input).orElse(null);
    }

    @MutationMapping
    public TripPlan removeTripStop(@Argument String tripId,
                                   @Argument int orderIndex) {
        return tripService.removeStop(tripId, orderIndex).orElse(null);
    }

    @MutationMapping
    public TripPlan rearrangeTripStops(@Argument String tripId,
                                       @Argument List<TripStop> stops) {
        return tripService.rearrangeStops(tripId, stops).orElse(null);
    }

    @MutationMapping
    public Boolean deleteTripPlan(@Argument String id) {
        return tripService.deleteById(id);
    }
}

