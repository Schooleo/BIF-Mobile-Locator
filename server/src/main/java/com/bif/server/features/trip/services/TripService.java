package com.bif.server.features.trip.services;

import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    public Optional<TripPlan> addStop(String tripId, TripStop stop) {
        Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
        optPlan.ifPresent(plan -> {
            List<TripStop> stops = plan.getStops();
            if (stops == null) {
                stops = new ArrayList<>();
            }
            stop.setOrderIndex(stops.size());
            stops.add(stop);
            plan.setStops(stops);
            tripPlanRepository.save(plan);
        });
        return optPlan;
    }

    public Optional<TripPlan> removeStop(String tripId, int orderIndex) {
        Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
        optPlan.ifPresent(plan -> {
            List<TripStop> stops = plan.getStops();
            if (stops != null && orderIndex >= 0 && orderIndex < stops.size()) {
                stops.removeIf(s -> s.getOrderIndex() == orderIndex);
                for (int i = 0; i < stops.size(); i++) {
                    stops.get(i).setOrderIndex(i);
                }
                plan.setStops(stops);
                tripPlanRepository.save(plan);
            }
        });
        return optPlan;
    }

    public Optional<TripPlan> rearrangeStops(String tripId, List<TripStop> newStops) {
        Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
        optPlan.ifPresent(plan -> {
            for (int i = 0; i < newStops.size(); i++) {
                newStops.get(i).setOrderIndex(i);
            }
            plan.setStops(newStops);
            tripPlanRepository.save(plan);
        });
        return optPlan;
    }

    public boolean deleteById(String id) {
        if (!tripPlanRepository.existsById(id)) {
            return false;
        }
        tripPlanRepository.deleteById(id);
        return true;
    }
}

