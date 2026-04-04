package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class TripStopSyncEntityHandler implements SyncEntityHandler {

    private final TripPlanRepository tripPlanRepository;
    private final ObjectMapper objectMapper;

    public TripStopSyncEntityHandler(TripPlanRepository tripPlanRepository,
                                     ObjectMapper objectMapper) {
        this.tripPlanRepository = tripPlanRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String entityType() {
        return "trip_stop";
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId,
                                    long newVersion) {
        TripStopPayload payload = parsePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        String stopId = pushed.getEntityId();
        if ((stopId == null || stopId.isBlank()) && payload != null) {
            stopId = payload.id;
        }
        String tripId = payload != null ? payload.tripId : null;

        if (stopId == null || stopId.isBlank() || tripId == null
                || tripId.isBlank()) {
            return pushed.getPayload();
        }

        Optional<TripPlan> planOpt = tripPlanRepository.findById(tripId);
        if (planOpt.isEmpty()) {
            return pushed.getPayload();
        }

        TripPlan plan = planOpt.get();
        List<TripStop> stops = plan.getStops() != null
                ? new ArrayList<>(plan.getStops())
                : new ArrayList<>();

        TripStop target = findStop(stops, stopId);
        if (target == null) {
            target = new TripStop();
            target.setId(stopId);
            stops.add(target);
        }

        boolean deleteRequested = "DELETE".equals(operation)
                || (payload != null && payload.deleted);

        if (payload != null) {
            target.setTitle(payload.title);
            target.setNote(payload.note);
            if (payload.photoUrlProvided) {
                target.setPhotoUrl(normalizeNullable(payload.photoUrl));
            }
            target.setOrderIndex(payload.orderIndex);
            target.setArrivalTime(payload.arrivalTime);
            target.setDepartureTime(payload.departureTime);
            if (payload.latitude != null && payload.longitude != null) {
                target.setLocation(new Location(payload.latitude,
                        payload.longitude));
            }
        }

        target.setDeleted(deleteRequested);
        target.setServerVersion(newVersion);

        plan.setStops(stops);
        plan.setServerVersion(newVersion);
        plan.setLastModifiedBy(userId);
        tripPlanRepository.save(plan);

        TripStopPayload responsePayload = toPayload(target, tripId);
        responsePayload.serverVersion = newVersion;
        return writePayload(responsePayload);
    }

    @Override
    public String resolvePayload(SyncChangeEntry entry) {
        TripStopPayload entryPayload = parsePayload(entry.getPayload());
        String stopId = entry.getEntityId();
        String tripId = entryPayload != null ? entryPayload.tripId : null;

        if (tripId == null || tripId.isBlank()) {
            return entry.getPayload();
        }

        Optional<TripPlan> planOpt = tripPlanRepository.findById(tripId);
        if (planOpt.isEmpty()) {
            return entry.getPayload();
        }

        TripStop stop = findStop(planOpt.get().getStops(), stopId);
        if (stop == null) {
            return entry.getPayload();
        }

        TripStopPayload payload = toPayload(stop, tripId);
        payload.serverVersion = Math.max(payload.serverVersion,
                entry.getServerVersion());
        return writePayload(payload);
    }

    private TripStop findStop(List<TripStop> stops, String stopId) {
        if (stops == null || stopId == null) {
            return null;
        }
        for (TripStop stop : stops) {
            if (stop != null && stopId.equals(stop.getId())) {
                return stop;
            }
        }
        return null;
    }

    private TripStopPayload parsePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            TripStopPayload payload = objectMapper.treeToValue(node, TripStopPayload.class);
            payload.photoUrlProvided = node.has("photoUrl");
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(TripStopPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private TripStopPayload toPayload(TripStop stop, String tripId) {
        TripStopPayload payload = new TripStopPayload();
        payload.id = stop.getId();
        payload.tripId = tripId;
        payload.title = stop.getTitle();
        payload.note = stop.getNote();
        payload.photoUrl = stop.getPhotoUrl();
        payload.arrivalTime = stop.getArrivalTime();
        payload.departureTime = stop.getDepartureTime();
        payload.orderIndex = stop.getOrderIndex();
        if (stop.getLocation() != null) {
            payload.latitude = stop.getLocation().getLatitude();
            payload.longitude = stop.getLocation().getLongitude();
        }
        payload.serverVersion = stop.getServerVersion();
        payload.deleted = stop.isDeleted();
        return payload;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class TripStopPayload {
        public String id;
        public String tripId;
        public String title;
        public String note;
        public String photoUrl;
        public Double latitude;
        public Double longitude;
        public Instant arrivalTime;
        public Instant departureTime;
        public int orderIndex;
        public long serverVersion;
        public boolean deleted;
        public boolean photoUrlProvided;
    }
}
