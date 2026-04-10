package com.bif.server.features.sync.services;

import com.bif.server.common.models.Location;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripStopSyncEntityHandlerTest {

    @Mock
    private TripPlanRepository tripPlanRepository;

    private TripStopSyncEntityHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        handler = new TripStopSyncEntityHandler(tripPlanRepository, objectMapper);
    }

    @Test
    void applyPushedChange_updatesOnlyTargetStopAndPreservesOthers()
            throws Exception {
        TripStop first = new TripStop();
        first.setId("s1");
        first.setTitle("A");
        first.setOrderIndex(0);

        TripStop second = new TripStop();
        second.setId("s2");
        second.setTitle("B");
        second.setOrderIndex(1);

        TripPlan plan = new TripPlan();
        plan.setId("trip-1");
        plan.setStops(new ArrayList<>(List.of(first, second)));

        when(tripPlanRepository.findById("trip-1")).thenReturn(Optional.of(plan));
        when(tripPlanRepository.save(any(TripPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("trip_stop");
        pushed.setEntityId("s1");
        pushed.setOperation("UPDATE");
        pushed.setPayload("{\"id\":\"s1\",\"tripId\":\"trip-1\","
                + "\"title\":\"Updated\",\"note\":\"n\","
                + "\"location\":{\"latitude\":1.2,\"longitude\":2.3},"
                + "\"arrivalTime\":\"2026-03-28T09:00:00Z\","
                + "\"departureTime\":\"2026-03-28T10:00:00Z\","
                + "\"orderIndex\":3,\"deleted\":false}");

        String payload = handler.applyPushedChange(pushed, "user-1", 17L);

        ArgumentCaptor<TripPlan> captor = ArgumentCaptor.forClass(TripPlan.class);
        verify(tripPlanRepository).save(captor.capture());
        TripPlan saved = captor.getValue();
        assertEquals(2, saved.getStops().size());
        assertEquals("Updated", saved.getStops().get(0).getTitle());
        assertEquals("B", saved.getStops().get(1).getTitle());
        assertEquals(17L, saved.getServerVersion());

        JsonNode response = objectMapper.readTree(payload);
        assertEquals("s1", response.get("id").asText());
        assertEquals("trip-1", response.get("tripId").asText());
        assertEquals(17L, response.get("serverVersion").asLong());
        assertNotNull(response.get("location"));
        assertEquals(1.2, response.get("location").get("latitude").asDouble(), 0.001);
        assertEquals(2.3, response.get("location").get("longitude").asDouble(), 0.001);
        assertNull(response.get("latitude"));
        assertNull(response.get("longitude"));
    }

    @Test
    void resolvePayload_usesTripIdFromEntryPayload() throws Exception {
        TripStop stop = new TripStop();
        stop.setId("s9");
        stop.setTitle("Stop 9");
        stop.setOrderIndex(4);
        stop.setServerVersion(2L);
        stop.setDeleted(false);
        stop.setLocation(new Location(4.4, 5.5));
        stop.setArrivalTime(Instant.parse("2026-03-28T09:00:00Z"));
        stop.setDepartureTime(Instant.parse("2026-03-28T10:00:00Z"));

        TripPlan plan = new TripPlan();
        plan.setId("trip-2");
        plan.setStops(List.of(stop));

        when(tripPlanRepository.findById("trip-2")).thenReturn(Optional.of(plan));

        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityId("s9");
        entry.setServerVersion(12L);
        entry.setPayload("{\"tripId\":\"trip-2\"}");

        String resolved = handler.resolvePayload(entry);
        assertNotNull(resolved);

        JsonNode response = objectMapper.readTree(resolved);
        assertEquals("s9", response.get("id").asText());
        assertEquals("trip-2", response.get("tripId").asText());
        assertEquals(12L, response.get("serverVersion").asLong());
    }

    @Test
    void applyPushedChange_WhenPhotoUrlBlank_NormalizesToNull() {
        TripStop existing = new TripStop();
        existing.setId("s1");
        existing.setPhotoUrl("https://res.cloudinary.com/demo/image/upload/v1/old.jpg");

        TripPlan plan = new TripPlan();
        plan.setId("trip-1");
        plan.setStops(new ArrayList<>(List.of(existing)));

        when(tripPlanRepository.findById("trip-1")).thenReturn(Optional.of(plan));
        when(tripPlanRepository.save(any(TripPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("trip_stop");
        pushed.setEntityId("s1");
        pushed.setOperation("UPDATE");
        pushed.setPayload("{\"id\":\"s1\",\"tripId\":\"trip-1\",\"photoUrl\":\"   \",\"orderIndex\":0}");

        handler.applyPushedChange(pushed, "user-1", 20L);

        ArgumentCaptor<TripPlan> captor = ArgumentCaptor.forClass(TripPlan.class);
        verify(tripPlanRepository).save(captor.capture());
        assertNull(captor.getValue().getStops().get(0).getPhotoUrl());
    }

    @Test
    void applyPushedChangeResult_whenTripMissing_rejectsValidation() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityType("trip_stop");
        pushed.setEntityId("s1");
        pushed.setOperation("UPDATE");
        pushed.setPayload("{\"id\":\"s1\",\"tripId\":\"missing-trip\","
                + "\"location\":{\"latitude\":1.0,\"longitude\":2.0}}");

        SyncPushApplyResult result =
                handler.applyPushedChangeResult(pushed, "user-1", () -> 21L);

        assertEquals(SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                result.getStatus());
        assertEquals("TRIP_NOT_FOUND", result.getReasonCode());
        verify(tripPlanRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void resolvePayload_emitsCanonicalNestedLocation() throws Exception {
        TripStop stop = new TripStop();
        stop.setId("s3");
        stop.setLocation(new Location(8.5, 9.5));

        TripPlan plan = new TripPlan();
        plan.setId("trip-3");
        plan.setStops(List.of(stop));

        when(tripPlanRepository.findById("trip-3")).thenReturn(Optional.of(plan));

        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityId("s3");
        entry.setServerVersion(4L);
        entry.setPayload("{\"tripId\":\"trip-3\"}");

        JsonNode response = objectMapper.readTree(handler.resolvePayload(entry));

        assertNotNull(response.get("location"));
        assertEquals(8.5, response.get("location").get("latitude").asDouble(), 0.001);
        assertEquals(9.5, response.get("location").get("longitude").asDouble(), 0.001);
        assertNull(response.get("latitude"));
        assertNull(response.get("longitude"));
    }
}
