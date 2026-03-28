package com.bif.server.features.sync.services;

import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.trip.models.TripPlan;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripSyncEntityHandlerTest {

    @Mock
    private TripPlanRepository tripPlanRepository;

    private TripSyncEntityHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        handler = new TripSyncEntityHandler(tripPlanRepository, objectMapper);
    }

    @Test
    void applyPushedChange_updatesTripMetadataAndReturnsPayload()
            throws Exception {
        TripPlan existing = new TripPlan();
        existing.setId("trip-1");
        when(tripPlanRepository.findById("trip-1"))
                .thenReturn(Optional.of(existing));
        when(tripPlanRepository.save(any(TripPlan.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("trip_plan");
        pushed.setEntityId("trip-1");
        pushed.setOperation("UPDATE");
        pushed.setPayload("{\"id\":\"trip-1\",\"groupId\":\"g-1\","
                + "\"title\":\"April\",\"description\":\"Desc\","
                + "\"startAt\":\"2026-03-28T09:00:00Z\","
                + "\"endAt\":\"2026-03-28T10:00:00Z\","
                + "\"deleted\":false}");

        String payload = handler.applyPushedChange(pushed, "user-1", 11L);

        ArgumentCaptor<TripPlan> captor = ArgumentCaptor.forClass(TripPlan.class);
        verify(tripPlanRepository).save(captor.capture());
        TripPlan saved = captor.getValue();
        assertEquals("g-1", saved.getGroupId());
        assertEquals("April", saved.getTitle());
        assertEquals(11L, saved.getServerVersion());
        assertEquals("user-1", saved.getLastModifiedBy());

        JsonNode response = objectMapper.readTree(payload);
        assertEquals("trip-1", response.get("id").asText());
        assertEquals(11L, response.get("serverVersion").asLong());
    }

    @Test
    void resolvePayload_readsTripFromRepository() throws Exception {
        TripPlan plan = new TripPlan();
        plan.setId("trip-1");
        plan.setGroupId("g-1");
        plan.setTitle("T");
        plan.setDescription("D");
        plan.setStartAt(Instant.parse("2026-03-28T09:00:00Z"));
        plan.setEndAt(Instant.parse("2026-03-28T10:00:00Z"));
        plan.setServerVersion(3L);
        when(tripPlanRepository.findById("trip-1")).thenReturn(Optional.of(plan));

        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityId("trip-1");
        entry.setServerVersion(9L);

        String resolved = handler.resolvePayload(entry);
        assertNotNull(resolved);

        JsonNode response = objectMapper.readTree(resolved);
        assertEquals("trip-1", response.get("id").asText());
        assertEquals(9L, response.get("serverVersion").asLong());
    }

        @Test
        void applyPushedChange_whenCreateAndGroupAtLimit_returnsDeletedPayload()
            throws Exception {
        when(tripPlanRepository.findById("trip-31"))
            .thenReturn(Optional.empty());
        when(tripPlanRepository.countByGroupIdAndDeletedFalse("g-1"))
            .thenReturn(30L);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("trip_plan");
        pushed.setEntityId("trip-31");
        pushed.setOperation("CREATE");
        pushed.setPayload("{\"id\":\"trip-31\",\"groupId\":\"g-1\","
            + "\"title\":\"Overflow\"}");

        String payload = handler.applyPushedChange(pushed, "user-1", 50L);

        JsonNode response = objectMapper.readTree(payload);
        assertEquals("trip-31", response.get("id").asText());
        assertEquals("g-1", response.get("groupId").asText());
        assertEquals(true, response.get("deleted").asBoolean());
        assertEquals(50L, response.get("serverVersion").asLong());
        verify(tripPlanRepository, org.mockito.Mockito.never()).save(any(TripPlan.class));
        }
}
