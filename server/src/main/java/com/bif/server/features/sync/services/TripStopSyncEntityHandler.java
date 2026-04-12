package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.LongSupplier;

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
    public SyncPushApplyResult applyPushedChangeResult(SyncChange pushed,
                                                       String userId,
                                                       LongSupplier nextVersionSupplier) {
        TripStopPayload payload = parsePayload(pushed.getPayload());
        String operation = pushed.getOperation() != null
                ? pushed.getOperation().toUpperCase(Locale.ROOT)
                : "UPDATE";

        String stopId = pushed.getEntityId();
        if ((stopId == null || stopId.isBlank()) && payload != null) {
            stopId = payload.id;
        }
        String tripId = payload != null ? payload.tripId : null;

        if (payload == null) {
            return SyncPushApplyResult.rejectedValidation("INVALID_PAYLOAD");
        }
        if (stopId == null || stopId.isBlank()) {
            return SyncPushApplyResult.rejectedValidation("MISSING_STOP_ID");
        }
        if (tripId == null || tripId.isBlank()) {
            return SyncPushApplyResult.rejectedValidation("MISSING_TRIP_ID");
        }

        Optional<TripPlan> planOpt = tripPlanRepository.findById(tripId);
        if (planOpt.isEmpty()) {
            return SyncPushApplyResult.rejectedValidation("TRIP_NOT_FOUND");
        }

        long newVersion = nextVersionSupplier.getAsLong();
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
                || payload.deleted;

        target.setPlaceId(payload.placeId);
        target.setTitle(payload.title);
        target.setAddress(payload.address);
        target.setNote(payload.note);
        if (payload.photoUrlProvided) {
            target.setPhotoUrl(normalizeNullable(payload.photoUrl));
        }
        target.setAddedByUserId(payload.addedByUserId);
        target.setAddedByName(payload.addedByName);
        target.setAddedByAvatarLetter(payload.addedByAvatarLetter);
        target.setAddedByAvatarColor(payload.addedByAvatarColor);
        target.setOrderIndex(payload.orderIndex);
        target.setArrivalTime(payload.arrivalTime);
        target.setDepartureTime(payload.departureTime);
        if (payload.latitude != null && payload.longitude != null) {
            target.setLocation(new Location(payload.latitude,
                    payload.longitude));
        }

        target.setDeleted(deleteRequested);
        target.setServerVersion(newVersion);

        plan.setStops(stops);
        plan.setServerVersion(newVersion);
        plan.setLastModifiedBy(userId);
        tripPlanRepository.save(plan);

        TripStopPayload responsePayload = toPayload(target, tripId);
        responsePayload.serverVersion = newVersion;
        return SyncPushApplyResult.applied(writePayload(responsePayload), newVersion);
    }

    @Override
    public String applyPushedChange(SyncChange pushed, String userId,
                                    long newVersion) {
        return applyPushedChangeResult(pushed, userId, () -> newVersion).getPayload();
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
            JsonNode locationNode = node.get("location");
            if (locationNode != null && locationNode.isObject()) {
                if (payload.latitude == null && locationNode.hasNonNull("latitude")) {
                    payload.latitude = locationNode.get("latitude").doubleValue();
                }
                if (payload.longitude == null && locationNode.hasNonNull("longitude")) {
                    payload.longitude = locationNode.get("longitude").doubleValue();
                }
            }
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    private String writePayload(TripStopPayload payload) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            putText(node, "id", payload.id);
            putText(node, "tripId", payload.tripId);
            putText(node, "placeId", payload.placeId);
            putText(node, "title", payload.title);
            putText(node, "address", payload.address);
            putText(node, "note", payload.note);
            putText(node, "photoUrl", payload.photoUrl);
            putText(node, "addedByUserId", payload.addedByUserId);
            putText(node, "addedByName", payload.addedByName);
            putText(node, "addedByAvatarLetter", payload.addedByAvatarLetter);
            if (payload.addedByAvatarColor != null) {
                node.put("addedByAvatarColor", payload.addedByAvatarColor);
            }
            if (payload.location != null && payload.location.latitude != null
                    && payload.location.longitude != null) {
                ObjectNode locationNode = node.putObject("location");
                locationNode.put("latitude", payload.location.latitude);
                locationNode.put("longitude", payload.location.longitude);
            }
            if (payload.arrivalTime != null) {
                node.set("arrivalTime", objectMapper.valueToTree(payload.arrivalTime));
            }
            if (payload.departureTime != null) {
                node.set("departureTime", objectMapper.valueToTree(payload.departureTime));
            }
            node.put("orderIndex", payload.orderIndex);
            node.put("serverVersion", payload.serverVersion);
            node.put("deleted", payload.deleted);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private TripStopPayload toPayload(TripStop stop, String tripId) {
        TripStopPayload payload = new TripStopPayload();
        payload.id = stop.getId();
        payload.tripId = tripId;
        payload.placeId = stop.getPlaceId();
        payload.title = stop.getTitle();
        payload.address = stop.getAddress();
        payload.note = stop.getNote();
        payload.photoUrl = stop.getPhotoUrl();
        payload.addedByUserId = stop.getAddedByUserId();
        payload.addedByName = stop.getAddedByName();
        payload.addedByAvatarLetter = stop.getAddedByAvatarLetter();
        payload.addedByAvatarColor = stop.getAddedByAvatarColor();
        payload.arrivalTime = stop.getArrivalTime();
        payload.departureTime = stop.getDepartureTime();
        payload.orderIndex = stop.getOrderIndex();
        if (stop.getLocation() != null) {
            payload.location = new LocationPayload();
            payload.location.latitude = stop.getLocation().getLatitude();
            payload.location.longitude = stop.getLocation().getLongitude();
        }
        payload.serverVersion = stop.getServerVersion();
        payload.deleted = stop.isDeleted();
        return payload;
    }

    private void putText(ObjectNode node, String fieldName, String value) {
        if (value != null) {
            node.put(fieldName, value);
        }
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
        public String placeId;
        public String title;
        public String address;
        public String note;
        public String photoUrl;
        public String addedByUserId;
        public String addedByName;
        public String addedByAvatarLetter;
        public Integer addedByAvatarColor;
        public LocationPayload location;
        public Double latitude;
        public Double longitude;
        public Instant arrivalTime;
        public Instant departureTime;
        public int orderIndex;
        public long serverVersion;
        public boolean deleted;
        public boolean photoUrlProvided;
    }

    private static class LocationPayload {
        public Double latitude;
        public Double longitude;
    }
}
