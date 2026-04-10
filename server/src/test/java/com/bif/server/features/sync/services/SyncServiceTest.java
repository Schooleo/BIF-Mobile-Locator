package com.bif.server.features.sync.services;

import com.bif.server.features.group.models.Group;
import com.bif.server.features.group.repositories.GroupRepository;
import com.bif.server.features.chat.repositories.ChatMessageRepository;
import com.bif.server.features.sync.models.*;
import com.bif.server.features.sync.repositories.SyncChangeRepository;
import com.bif.server.features.trip.repositories.TripPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncServiceTest {

    @Mock
    private SyncVersionService syncVersionService;

    @Mock
    private SyncChangeRepository syncChangeRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private TripPlanRepository tripPlanRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

        @Mock
        private SyncEntityHandler placeSyncEntityHandler;

        @Mock
        private SyncEntityHandler tripSyncEntityHandler;

        @Mock
        private SyncEntityHandler tripStopSyncEntityHandler;

        @Mock
        private SyncEntityHandler friendshipSyncEntityHandler;

    private SyncService syncService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(placeSyncEntityHandler.entityType()).thenReturn("place");
        when(tripSyncEntityHandler.entityType()).thenReturn("trip_plan");
        when(tripStopSyncEntityHandler.entityType()).thenReturn("trip_stop");
        when(friendshipSyncEntityHandler.entityType()).thenReturn("friendship");
        lenient().when(placeSyncEntityHandler.applyPushedChangeResult(any(), any(), any()))
                .thenCallRealMethod();
        lenient().when(tripSyncEntityHandler.applyPushedChangeResult(any(), any(), any()))
                .thenCallRealMethod();
        lenient().when(tripStopSyncEntityHandler.applyPushedChangeResult(any(), any(), any()))
                .thenCallRealMethod();
        lenient().when(friendshipSyncEntityHandler.applyPushedChangeResult(any(), any(), any()))
                .thenCallRealMethod();
        syncService = new SyncService(syncVersionService,
                syncChangeRepository,
                groupRepository,
                tripPlanRepository,
                chatMessageRepository,
                objectMapper,
                List.of(placeSyncEntityHandler,
                        tripSyncEntityHandler,
                        tripStopSyncEntityHandler,
                        friendshipSyncEntityHandler));
    }

    @Test
    void sync_WhenNoPushChanges_ReturnsPulledChanges() {
        SyncRequest request = new SyncRequest();
                request.setUserId("user1");
        request.setLastPulledVersion(5);
        request.setPushedChanges(null);

        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityType("place");
        entry.setEntityId("p1");
        entry.setServerVersion(6);
        entry.setOperation("CREATE");
        entry.setPayload("{\"id\":\"p1\",\"name\":\"Payload Place\"}");
        entry.setTimestamp(Instant.now());

        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(List.of(entry));
        when(placeSyncEntityHandler.resolvePayload(entry))
                .thenReturn(entry.getPayload());
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);

        SyncResponse response = syncService.sync(request);

        assertEquals(6, response.getCurrentServerVersion());
        assertEquals(1, response.getPulledChanges().size());
        assertEquals("place", response.getPulledChanges().get(0).getEntityType());
        assertEquals("{\"id\":\"p1\",\"name\":\"Payload Place\"}",
                response.getPulledChanges().get(0).getPayload());
        assertNull(response.getConflicts());
        assertNull(response.getPushResults());
    }

    @Test
    void sync_WhenPullContainsJustPushedClientChangeId_filtersEchoedEntry() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("client-echo");
        request.setPushedChanges(List.of(pushed));

        SyncChangeEntry echoedEntry = new SyncChangeEntry();
        echoedEntry.setEntityType("place");
        echoedEntry.setEntityId("p1");
        echoedEntry.setServerVersion(6);
        echoedEntry.setOperation("UPDATE");
        echoedEntry.setClientChangeId("client-echo");
        echoedEntry.setPayload("{\"id\":\"p1\"}");

        when(syncChangeRepository.findByClientChangeId("client-echo"))
                .thenReturn(Optional.empty());
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);
        doAnswer(invocation -> SyncPushApplyResult.applied(
                "{\"id\":\"p1\"}",
                ((java.util.function.LongSupplier) invocation.getArgument(2)).getAsLong()))
                .when(placeSyncEntityHandler).applyPushedChangeResult(any(), any(), any());
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(List.of(echoedEntry));

        SyncResponse response = syncService.sync(request);

        assertNotNull(response);
        assertTrue(response.getPulledChanges().isEmpty());
    }

    @Test
    void sync_WhenPushChanges_PersistsToChangeLog() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setServerVersion(5);
        pushed.setOperation("CREATE");
        pushed.setClientChangeId("client-1");
        pushed.setPayload("{\"id\":\"p1\",\"name\":\"Cafe\","
                + "\"address\":\"A\",\"rating\":4.2,"
                + "\"latitude\":1.0,\"longitude\":2.0}");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("client-1"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "place", "p1"))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> SyncPushApplyResult.applied(
                "{\"id\":\"p1\"}",
                ((java.util.function.LongSupplier) invocation.getArgument(2)).getAsLong()))
                .when(placeSyncEntityHandler).applyPushedChangeResult(any(), any(), any());
        when(syncVersionService.getCurrentVersion()).thenReturn(5L);
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);
        assertNotNull(response);

        ArgumentCaptor<SyncChangeEntry> captor =
                ArgumentCaptor.forClass(SyncChangeEntry.class);
        verify(syncChangeRepository).save(captor.capture());
        SyncChangeEntry saved = captor.getValue();
        assertEquals("place", saved.getEntityType());
        assertEquals("p1", saved.getEntityId());
        assertEquals(6L, saved.getServerVersion());
        assertEquals("client-1", saved.getClientChangeId());
        assertEquals("user1", saved.getUserId());
                assertNotNull(saved.getPayload());
        verify(placeSyncEntityHandler)
                .applyPushedChangeResult(any(), any(), any());
        assertNotNull(response.getPushResults());
        assertEquals(1, response.getPushResults().size());
        assertEquals("client-1", response.getPushResults().get(0).getClientChangeId());
        assertEquals(SyncPushApplyResult.STATUS_APPLIED,
                response.getPushResults().get(0).getStatus());
    }

    @Test
    void sync_WhenDuplicateClientChangeId_ReturnsAlreadyAppliedResult() {
        SyncRequest request = new SyncRequest();
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setClientChangeId("already-processed");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("already-processed"))
                .thenReturn(Optional.of(new SyncChangeEntry()));
        when(syncVersionService.getCurrentVersion()).thenReturn(5L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        null, 5))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        verify(syncChangeRepository, never()).save(any());
        verify(placeSyncEntityHandler, never())
                .applyPushedChangeResult(any(), any(), any());
        assertNotNull(response.getPushResults());
        assertEquals(1, response.getPushResults().size());
        assertEquals(SyncPushApplyResult.STATUS_ALREADY_APPLIED,
                response.getPushResults().get(0).getStatus());
        assertEquals("DUPLICATE_CLIENT_CHANGE_ID",
                response.getPushResults().get(0).getReasonCode());
    }

    @Test
    void sync_WhenVersionConflict_ReportsConflictButAccepts() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setServerVersion(3); // Client has version 3, server is at 5
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("conflict-1");
        pushed.setPayload("{\"id\":\"p1\"}");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("conflict-1"))
                .thenReturn(Optional.empty());
        SyncChangeEntry latestEntityVersion = new SyncChangeEntry();
        latestEntityVersion.setServerVersion(5);
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "place", "p1"))
                .thenReturn(Optional.of(latestEntityVersion));
        when(placeSyncEntityHandler.applyPushedChange(any(), any(), anyLong()))
                .thenReturn(pushed.getPayload());
        when(syncVersionService.nextVersion()).thenReturn(6L);
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        // Change still saved (LWW)
        verify(syncChangeRepository).save(any());
        // But conflict reported
        assertNotNull(response.getConflicts());
        assertEquals(1, response.getConflicts().size());
        SyncConflict conflict = response.getConflicts().get(0);
        assertEquals("place", conflict.getEntityType());
        assertEquals("p1", conflict.getEntityId());
        assertEquals(3, conflict.getClientVersion());
        assertEquals(5, conflict.getServerVersion());
        assertEquals("SERVER_WINS", conflict.getResolution());
        assertEquals(SyncPushApplyResult.STATUS_APPLIED,
                response.getPushResults().get(0).getStatus());
    }

    @Test
    void sync_WhenHandlerRejectsValidation_DoesNotPersistChangeLog() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(5);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("trip_stop");
        pushed.setEntityId("s1");
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("reject-1");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("reject-1"))
                .thenReturn(Optional.empty());
        when(syncVersionService.getCurrentVersion()).thenReturn(6L);
        doReturn(SyncPushApplyResult.rejectedValidation("TRIP_NOT_FOUND"))
                .when(tripStopSyncEntityHandler)
                .applyPushedChangeResult(any(), any(), any());
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 5))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        verify(syncChangeRepository, never()).save(any());
        assertNotNull(response.getPushResults());
        assertEquals(SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                response.getPushResults().get(0).getStatus());
        assertEquals("TRIP_NOT_FOUND",
                response.getPushResults().get(0).getReasonCode());
    }

    @Test
    void sync_WhenFriendshipChangePushed_UsesFriendshipHandler() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(3);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("friendship");
        pushed.setEntityId("friendship-1");
        pushed.setOperation("ACCEPT_REQUEST");
        pushed.setClientChangeId("friendship-change");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("friendship-change"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "friendship", "friendship-1"))
                .thenReturn(Optional.empty());
        when(syncVersionService.getCurrentVersion()).thenReturn(4L);
        doReturn(SyncPushApplyResult.applied("{\"id\":\"friendship-1\"}", 4L))
                .when(friendshipSyncEntityHandler).applyPushedChangeResult(any(), any(), any());
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 3))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        assertNotNull(response);
        verify(friendshipSyncEntityHandler).applyPushedChangeResult(any(), any(), any());
        assertEquals("friendship-change", response.getPushResults().get(0).getClientChangeId());
        assertEquals(SyncPushApplyResult.STATUS_APPLIED, response.getPushResults().get(0).getStatus());
    }

    @Test
    void sync_WhenUnsupportedEntityType_PushResultRejectsValidation() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(0);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("unsupported");
        pushed.setEntityId("x1");
        pushed.setClientChangeId("unsupported-1");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("unsupported-1"))
                .thenReturn(Optional.empty());
        when(syncVersionService.getCurrentVersion()).thenReturn(0L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 0))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        verify(syncChangeRepository, never()).save(any());
        assertEquals(SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                response.getPushResults().get(0).getStatus());
        assertEquals("UNSUPPORTED_ENTITY_TYPE",
                response.getPushResults().get(0).getReasonCode());
    }

    @Test
    void sync_WhenGlobalVersionAheadButSameEntityNotAhead_DoesNotConflict() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(10);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p1");
        pushed.setServerVersion(10);
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("place-no-conflict");
        pushed.setPayload("{\"id\":\"p1\"}");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("place-no-conflict"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "place", "p1"))
                .thenReturn(Optional.of(new SyncChangeEntry()));
        when(syncVersionService.getCurrentVersion()).thenReturn(12L);
        when(syncVersionService.nextVersion()).thenReturn(12L);
        when(placeSyncEntityHandler.applyPushedChange(any(), any(), anyLong()))
                .thenReturn(pushed.getPayload());
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 10))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        assertNotNull(response);
        assertNull(response.getConflicts());
    }

    @Test
    void sync_WhenBaselineNegative_UsesZero() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(-2);
        request.setPushedChanges(null);

        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 0))
                .thenReturn(Collections.emptyList());
        when(syncVersionService.getCurrentVersion()).thenReturn(0L);

        SyncResponse response = syncService.sync(request);

        assertEquals(0, response.getCurrentServerVersion());
        assertTrue(response.getPulledChanges().isEmpty());
    }

    @Test
    void sync_PullRemainsScopedToRequestingUser() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user-a");
        request.setLastPulledVersion(4);

        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user-a", 4))
                .thenReturn(Collections.emptyList());
        when(syncVersionService.getCurrentVersion()).thenReturn(4L);

        SyncResponse response = syncService.sync(request);

        assertNotNull(response);
        verify(syncChangeRepository)
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user-a", 4);
        verify(syncChangeRepository, never())
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user-b", 4);
    }

    @Test
    void sync_WhenGroupUpdated_FanoutsChangeLogToOwnerAndMembers() {
        SyncRequest request = new SyncRequest();
        request.setUserId("owner-1");
        request.setLastPulledVersion(2);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("group");
        pushed.setEntityId("group-1");
        pushed.setOperation("UPDATE");
        pushed.setClientChangeId("group-change");
        pushed.setPayload("{\"id\":\"group-1\"}");
        request.setPushedChanges(List.of(pushed));

        SyncEntityHandler groupHandler = mock(SyncEntityHandler.class);
        when(groupHandler.entityType()).thenReturn("group");
        when(groupHandler.applyPushedChangeResult(any(), any(), any()))
                .thenReturn(SyncPushApplyResult.applied("{\"id\":\"group-1\"}", 3L));

        SyncService fanoutService = new SyncService(
                syncVersionService,
                syncChangeRepository,
                groupRepository,
                tripPlanRepository,
                chatMessageRepository,
                objectMapper,
                List.of(groupHandler)
        );

        when(syncChangeRepository.findByClientChangeId("group-change"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "owner-1", "group", "group-1"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "owner-1", 2))
                .thenReturn(Collections.emptyList());
        when(syncVersionService.getCurrentVersion()).thenReturn(3L);

        Group group = new Group();
        group.setId("group-1");
        group.setOwnerId("owner-1");
        group.setMemberIds(List.of("owner-1", "member-1", "member-2"));
        when(groupRepository.findById("group-1")).thenReturn(Optional.of(group));

        fanoutService.sync(request);

        ArgumentCaptor<SyncChangeEntry> entryCaptor = ArgumentCaptor.forClass(SyncChangeEntry.class);
        verify(syncChangeRepository, times(3)).save(entryCaptor.capture());
        List<SyncChangeEntry> entries = entryCaptor.getAllValues();
        assertEquals("owner-1", entries.get(0).getUserId());
        assertEquals("group-change", entries.get(0).getClientChangeId());
        assertEquals("member-1", entries.get(1).getUserId());
        assertNull(entries.get(1).getClientChangeId());
        assertEquals("member-2", entries.get(2).getUserId());
        assertNull(entries.get(2).getClientChangeId());
    }

    @Test
    void sync_WhenNullClientChangeId_RejectsValidation() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(0);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("favorite");
        pushed.setEntityId("f1");
        pushed.setServerVersion(0);
        pushed.setOperation("CREATE");
        pushed.setClientChangeId(null);
        request.setPushedChanges(List.of(pushed));

        when(syncVersionService.getCurrentVersion()).thenReturn(0L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 0))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        verify(syncChangeRepository, never()).findByClientChangeId(any());
        verify(syncChangeRepository, never()).save(any());
        assertNotNull(response.getPushResults());
        assertEquals(SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                response.getPushResults().get(0).getStatus());
        assertEquals("MISSING_CLIENT_CHANGE_ID",
                response.getPushResults().get(0).getReasonCode());
    }

    @Test
        void sync_WhenDeletePlacePush_UsesPlaceHandlerAndStoresPayload() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(2);

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("place");
        pushed.setEntityId("p-del");
        pushed.setOperation("DELETE");
        pushed.setClientChangeId("delete-1");
        request.setPushedChanges(List.of(pushed));

        when(syncChangeRepository.findByClientChangeId("delete-1"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "place", "p-del"))
                .thenReturn(Optional.empty());
        when(syncVersionService.getCurrentVersion()).thenReturn(2L);
        when(syncVersionService.nextVersion()).thenReturn(3L);
        doAnswer(invocation -> SyncPushApplyResult.applied(
                "{\"id\":\"p-del\",\"deleted\":true}",
                ((java.util.function.LongSupplier) invocation.getArgument(2)).getAsLong()))
                .when(placeSyncEntityHandler).applyPushedChangeResult(any(), any(), any());
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 2))
                .thenReturn(Collections.emptyList());

        syncService.sync(request);

        verify(placeSyncEntityHandler)
                .applyPushedChangeResult(any(), any(), any());

        ArgumentCaptor<SyncChangeEntry> changeCaptor =
                ArgumentCaptor.forClass(SyncChangeEntry.class);
        verify(syncChangeRepository).save(changeCaptor.capture());
        assertNotNull(changeCaptor.getValue().getPayload());
        assertTrue(changeCaptor.getValue().getPayload().contains("\"deleted\":true"));
    }

    @Test
    void sync_WhenTripEntitiesPushed_UsesMatchingHandlers() {
        SyncRequest request = new SyncRequest();
        request.setUserId("user1");
        request.setLastPulledVersion(20);

        SyncChange tripPush = new SyncChange();
        tripPush.setEntityType("trip_plan");
        tripPush.setEntityId("trip-1");
        tripPush.setOperation("UPDATE");
        tripPush.setClientChangeId("trip-change");

        SyncChange stopPush = new SyncChange();
        stopPush.setEntityType("trip_stop");
        stopPush.setEntityId("stop-1");
        stopPush.setOperation("UPDATE");
        stopPush.setClientChangeId("stop-change");

        request.setPushedChanges(List.of(tripPush, stopPush));

        when(syncChangeRepository.findByClientChangeId("trip-change"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository.findByClientChangeId("stop-change"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "trip_plan", "trip-1"))
                .thenReturn(Optional.empty());
        when(syncChangeRepository
                .findTopByUserIdAndEntityTypeAndEntityIdOrderByServerVersionDesc(
                        "user1", "trip_stop", "stop-1"))
                .thenReturn(Optional.empty());

        doAnswer(invocation -> SyncPushApplyResult.applied(
                "{\"id\":\"trip-1\"}",
                ((java.util.function.LongSupplier) invocation.getArgument(2)).getAsLong()))
                .when(tripSyncEntityHandler).applyPushedChangeResult(any(), any(), any());
        doAnswer(invocation -> SyncPushApplyResult.applied(
                "{\"id\":\"stop-1\"}",
                ((java.util.function.LongSupplier) invocation.getArgument(2)).getAsLong()))
                .when(tripStopSyncEntityHandler).applyPushedChangeResult(any(), any(), any());

        when(syncVersionService.nextVersion()).thenReturn(21L, 22L);
        when(syncVersionService.getCurrentVersion()).thenReturn(22L);
        when(syncChangeRepository
                .findByUserIdAndServerVersionGreaterThanOrderByServerVersionAsc(
                        "user1", 20))
                .thenReturn(Collections.emptyList());

        SyncResponse response = syncService.sync(request);

        assertNotNull(response);
        verify(tripSyncEntityHandler).applyPushedChangeResult(any(), eq("user1"), any());
        verify(tripStopSyncEntityHandler).applyPushedChangeResult(any(), eq("user1"), any());
    }
}
