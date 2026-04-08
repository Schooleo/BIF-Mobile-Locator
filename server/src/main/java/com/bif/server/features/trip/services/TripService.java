package com.bif.server.features.trip.services;

import com.bif.server.features.trip.exceptions.TripLimitExceededException;
import com.bif.server.features.trip.models.RearrangeStopInput;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TripService {
    private static final int MAX_TRIPS_PER_GROUP = 30;

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
        if (isCreateOperation(tripPlan)
                && tripPlan.getGroupId() != null
                && !tripPlan.getGroupId().isBlank()) {
            long groupTripCount = tripPlanRepository
                    .countByGroupIdAndDeletedFalse(tripPlan.getGroupId());
            if (groupTripCount >= MAX_TRIPS_PER_GROUP) {
                throw new TripLimitExceededException(
                        "Maximum 30 trips per group is reached");
            }
        }
        return tripPlanRepository.save(tripPlan);
    }

    private boolean isCreateOperation(TripPlan tripPlan) {
        if (tripPlan == null) {
            return false;
        }
        if (tripPlan.getId() == null || tripPlan.getId().isBlank()) {
            return true;
        }
        return !tripPlanRepository.existsById(tripPlan.getId());
    }

    public Optional<TripPlan> addStop(String tripId, TripStop stop) {
        int retries = 0;
        while (retries < 3) {
            try {
                Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
                if (optPlan.isEmpty()) {
                    return optPlan;
                }
                TripPlan plan = optPlan.get();
                List<TripStop> stops = plan.getStops() != null ? plan.getStops() : new ArrayList<>();
                stop.setOrderIndex(stops.size());
                stops.add(stop);
                plan.setStops(stops);
                return Optional.of(tripPlanRepository.save(plan));
            } catch (OptimisticLockingFailureException e) {
                retries++;
                if (retries >= 3) {
                    throw e;
                }
            }
        }
        return Optional.empty();
    }

    public Optional<TripPlan> removeStop(String tripId, String stopId) {
        int retries = 0;
        while (retries < 3) {
            try {
                Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
                if (optPlan.isEmpty()) {
                    return optPlan;
                }
                TripPlan plan = optPlan.get();
                List<TripStop> stops = plan.getStops();
                if (stops != null) {
                    stops.removeIf(s -> s.getId().equals(stopId));
                    for (int i = 0; i < stops.size(); i++) {
                        stops.get(i).setOrderIndex(i);
                    }
                    plan.setStops(stops);
                    return Optional.of(tripPlanRepository.save(plan));
                }
                return optPlan;
            } catch (OptimisticLockingFailureException e) {
                retries++;
                if (retries >= 3) {
                    throw e;
                }
            }
        }
        return Optional.empty();
    }

    public Optional<TripPlan> rearrangeStops(String tripId,
                                             List<RearrangeStopInput> stopOrders) {
        int retries = 0;
        while (retries < 3) {
            try {
                Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
                if (optPlan.isEmpty()) {
                    return optPlan;
                }
                TripPlan plan = optPlan.get();
                List<TripStop> stops = plan.getStops();
                if (stops == null || stops.isEmpty()) {
                    return optPlan;
                }

                Map<String, Integer> orderByStopId = new HashMap<>();
                if (stopOrders != null) {
                    for (RearrangeStopInput stopOrder : stopOrders) {
                        if (stopOrder == null || stopOrder.getId() == null
                                || stopOrder.getId().isBlank()) {
                            continue;
                        }
                        orderByStopId.put(stopOrder.getId(), stopOrder.getOrderIndex());
                    }
                }

                for (TripStop stop : stops) {
                    if (stop == null || stop.getId() == null) {
                        continue;
                    }
                    Integer updatedOrderIndex = orderByStopId.get(stop.getId());
                    if (updatedOrderIndex != null) {
                        stop.setOrderIndex(updatedOrderIndex);
                    }
                }

                stops.sort(Comparator.comparingInt(TripStop::getOrderIndex));
                for (int i = 0; i < stops.size(); i++) {
                    stops.get(i).setOrderIndex(i);
                }

                plan.setStops(stops);
                return Optional.of(tripPlanRepository.save(plan));
            } catch (OptimisticLockingFailureException e) {
                retries++;
                if (retries >= 3) {
                    throw e;
                }
            }
        }
        return Optional.empty();
    }

    public boolean deleteById(String id) {
        if (!tripPlanRepository.existsById(id)) {
            return false;
        }
        tripPlanRepository.deleteById(id);
        return true;
    }
}

