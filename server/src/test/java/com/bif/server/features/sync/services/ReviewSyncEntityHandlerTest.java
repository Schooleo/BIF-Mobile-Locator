package com.bif.server.features.sync.services;

import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.services.RatingService;
import com.bif.server.features.sync.models.SyncChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSyncEntityHandlerTest {

    @Mock
    private RatingService ratingService;

    private ReviewSyncEntityHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReviewSyncEntityHandler(ratingService, new ObjectMapper());
    }

    @Test
    void applyPushedChange_WhenEntityUserDoesNotMatchAuthenticatedUser_Throws() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("place-1:attacker");
        pushed.setOperation("CREATE");
        pushed.setPayload("{\"rating\":5,\"comment\":\"ok\"}");

        assertThrows(IllegalArgumentException.class,
                () -> handler.applyPushedChange(pushed, "u1", 1L));

        verifyNoInteractions(ratingService);
    }

    @Test
    void applyPushedChange_WhenPayloadCannotBeParsed_Throws() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("place-1:u1");
        pushed.setOperation("CREATE");
        pushed.setPayload("{invalid-json");

        assertThrows(IllegalArgumentException.class,
                () -> handler.applyPushedChange(pushed, "u1", 1L));

        verifyNoInteractions(ratingService);
    }

    @Test
    void applyPushedChange_WhenPayloadContainsDifferentUserId_UsesAuthenticatedUser() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("place-1:u1");
        pushed.setOperation("CREATE");
        pushed.setPayload("{\"placeId\":\"place-2\",\"userId\":\"attacker\",\"rating\":4,\"comment\":\"nice\"}");

        ReviewResponseDTO persisted = new ReviewResponseDTO(
            "r1",
            "place-1",
            "u1",
            "Anonymous",
            4,
            "nice",
            Instant.parse("2026-01-01T00:00:00Z"));
        when(ratingService.saveOrUpdateReview(4, "nice", "u1", "place-1", 2L))
            .thenReturn(persisted);

        String payload = handler.applyPushedChange(pushed, "u1", 2L);

        verify(ratingService).saveOrUpdateReview(
            eq(4),
            eq("nice"),
            eq("u1"),
            eq("place-1"),
            eq(2L));
        assertTrue(payload.contains("\"placeId\":\"place-1\""));
        assertFalse(payload.contains("\"placeId\":\"place-2\""));
        assertTrue(payload.contains("\"userId\":\"u1\""));
    }
}
