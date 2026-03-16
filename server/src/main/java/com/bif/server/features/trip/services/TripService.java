package com.bif.server.features.trip.services;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TripService {
    private final TripPlanRepository tripPlanRepository;

    public TripService(TripPlanRepository tripPlanRepository) {
        this.tripPlanRepository = tripPlanRepository;
    }

    public List<TripPlan> getAll() {
        return tripPlanRepository.findAll();
    }

    public List<TripPlan> getByGroupId(String groupId) {
        return tripPlanRepository.findByGroupId(groupId);
    }

    public Optional<TripPlan> getById(String id) {
        return tripPlanRepository.findById(id);
    }

    public TripPlan save(TripPlan tripPlan) {
        return tripPlanRepository.save(tripPlan);
    }

    public boolean deleteById(String id) {
        if (!tripPlanRepository.existsById(id)) {
            return false;
        }
        tripPlanRepository.deleteById(id);
        return true;
    }
}
