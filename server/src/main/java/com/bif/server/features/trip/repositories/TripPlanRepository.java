package com.bif.server.features.trip.repositories;

import com.bif.server.features.trip.models.TripPlan;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TripPlanRepository extends MongoRepository<TripPlan, String> {
    List<TripPlan> findByGroupId(String groupId);
}