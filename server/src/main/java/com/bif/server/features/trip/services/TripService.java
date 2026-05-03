package com.bif.server.features.trip.services;

import com.bif.server.features.group.services.GroupService;
import com.bif.server.features.trip.exceptions.TripAccessDeniedException;
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
    private final GroupService groupService;

    public TripService(TripPlanRepository tripPlanRepository, GroupService groupService) {
        this.tripPlanRepository = tripPlanRepository;
        this.groupService = groupService;
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
        if (isCreateOperation(tripPlan)) {
            if (tripPlan == null || tripPlan.getGroupId() == null || tripPlan.getGroupId().isBlank()) {
                throw new IllegalArgumentException("Trip groupId is required for create operation");
            }

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

    public Optional<TripPlan> addStop(String tripId, String actorUserId, TripStop stop) {
        int retries = 0;
        while (retries < 3) {
            try {
                Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
                if (optPlan.isEmpty()) {
                    return optPlan;
                }
                TripPlan plan = optPlan.get();
                requireGroupMember(plan.getGroupId(), actorUserId);
                
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

    public Optional<TripPlan> removeStop(String tripId, String actorUserId, String stopId) {
        int retries = 0;
        while (retries < 3) {
            try {
                Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
                if (optPlan.isEmpty()) {
                    return optPlan;
                }
                TripPlan plan = optPlan.get();
                requireGroupMember(plan.getGroupId(), actorUserId);

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
                                             String actorUserId,
                                             List<RearrangeStopInput> stopOrders) {
        int retries = 0;
        while (retries < 3) {
            try {
                Optional<TripPlan> optPlan = tripPlanRepository.findById(tripId);
                if (optPlan.isEmpty()) {
                    return optPlan;
                }
                TripPlan plan = optPlan.get();
                requireGroupMember(plan.getGroupId(), actorUserId);

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

    private void requireGroupMember(String groupId, String actorUserId) {
        if (groupId == null || groupId.isBlank()) {
            throw new TripAccessDeniedException("Trip groupId is required to modify trip stops");
        }
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new TripAccessDeniedException("User must be authenticated to modify trip stops");
        }
        
        try {
            // GroupService will throw a SecurityException if the user is not a member
            groupService.getMembers(groupId, actorUserId);
        } catch (SecurityException e) {
            throw new TripAccessDeniedException("User must be a member of the group to modify trip stops");
        }
    }
}

