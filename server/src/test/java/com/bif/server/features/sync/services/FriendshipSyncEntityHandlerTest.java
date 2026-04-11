package com.bif.server.features.sync.services;

import com.bif.server.features.friendship.models.Friendship;
import com.bif.server.features.friendship.models.FriendshipStatus;
import com.bif.server.features.friendship.repositories.FriendshipRepository;
import com.bif.server.features.friendship.services.FriendshipService;
import com.bif.server.features.sync.models.SyncChange;
import com.bif.server.features.sync.models.SyncChangeEntry;
import com.bif.server.features.user.models.User;
import com.bif.server.features.user.services.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipSyncEntityHandlerTest {

    @Mock
    private FriendshipService friendshipService;
    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserService userService;

    private FriendshipSyncEntityHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new FriendshipSyncEntityHandler(friendshipService,
                friendshipRepository, userService, objectMapper);
    }

    @Test
    void applyPushedChange_acceptRequest_returnsFriendshipPayload() throws Exception {
        Friendship friendship = new Friendship();
        friendship.setId("friendship-1");
        friendship.setRequesterId("requester-1");
        friendship.setReceiverId("receiver-1");
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setCreatedAt(Instant.parse("2026-04-11T00:00:00Z"));
        friendship.setUpdatedAt(Instant.parse("2026-04-11T00:01:00Z"));

        User requester = new User();
        requester.setId("requester-1");
        requester.setUsername("Alice");

        when(friendshipService.acceptRequest("friendship-1", "receiver-1"))
                .thenReturn(friendship);
        when(friendshipRepository.save(any(Friendship.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.getById("requester-1")).thenReturn(Optional.of(requester));

        SyncChange pushed = new SyncChange();
        pushed.setEntityType("friendship");
        pushed.setEntityId("friendship-1");
        pushed.setOperation("ACCEPT_REQUEST");

        String payload = handler.applyPushedChange(pushed, "receiver-1", 7L);

        JsonNode json = objectMapper.readTree(payload);
        assertEquals("friendship-1", json.get("id").asText());
        assertEquals("requester-1", json.get("requesterId").asText());
        assertEquals("Alice", json.get("requesterName").asText());
        assertEquals("receiver-1", json.get("receiverId").asText());
        assertEquals("ACCEPTED", json.get("status").asText());
    }

    @Test
    void resolvePayload_mapsRepositoryFriendship() throws Exception {
        Friendship friendship = new Friendship();
        friendship.setId("friendship-2");
        friendship.setRequesterId("requester-2");
        friendship.setReceiverId("receiver-2");
        friendship.setStatus(FriendshipStatus.PENDING);
        friendship.setCreatedAt(Instant.parse("2026-04-11T00:00:00Z"));
        friendship.setUpdatedAt(Instant.parse("2026-04-11T00:01:00Z"));

        User requester = new User();
        requester.setId("requester-2");
        requester.setUsername("Bob");

        when(friendshipRepository.findById("friendship-2"))
                .thenReturn(Optional.of(friendship));
        when(userService.getById("requester-2")).thenReturn(Optional.of(requester));

        SyncChangeEntry entry = new SyncChangeEntry();
        entry.setEntityId("friendship-2");
        entry.setPayload("{\"id\":\"friendship-2\"}");

        String payload = handler.resolvePayload(entry);

        JsonNode json = objectMapper.readTree(payload);
        assertNotNull(json);
        assertEquals("friendship-2", json.get("id").asText());
        assertEquals("Bob", json.get("requesterName").asText());
        assertEquals("PENDING", json.get("status").asText());
    }

    @Test
    void applyPushedChange_sendRequest_missingPayload_returnsOriginalPayload() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityType("friendship");
        pushed.setOperation("SEND_REQUEST");
        pushed.setPayload(null);

        String payload = handler.applyPushedChange(pushed, "user-1", 1L);

        assertEquals(null, payload);
    }

    @Test
    void applyPushedChangeResult_sendRequest_missingPayload_rejectsValidation() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityType("friendship");
        pushed.setOperation("SEND_REQUEST");
        pushed.setPayload(null);

        SyncPushApplyResult result =
                handler.applyPushedChangeResult(pushed, "user-1", () -> 1L);

        assertEquals(SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                result.getStatus());
        assertEquals("INVALID_FRIENDSHIP_PAYLOAD", result.getReasonCode());
        assertNull(result.getPayload());
        org.mockito.Mockito.verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void applyPushedChangeResult_unknownOperation_rejectsValidation() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityType("friendship");
        pushed.setOperation("CANCEL_REQUEST");
        pushed.setEntityId("friendship-1");

        SyncPushApplyResult result =
                handler.applyPushedChangeResult(pushed, "user-1", () -> 2L);

        assertEquals(SyncPushApplyResult.STATUS_REJECTED_VALIDATION,
                result.getStatus());
        assertEquals("UNSUPPORTED_FRIENDSHIP_OPERATION", result.getReasonCode());
        assertNull(result.getPayload());
        org.mockito.Mockito.verify(friendshipRepository, never()).save(any(Friendship.class));
    }
}
