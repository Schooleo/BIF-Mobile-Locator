package com.bif.server.features.sync.services;

import com.bif.server.features.place.dto.rest.ReviewResponseDTO;
import com.bif.server.features.place.services.PlaceIdentityService;
import com.bif.server.features.place.services.RatingService;
import com.bif.server.features.sync.models.SyncChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock
    private PlaceIdentityService placeIdentityService;

    private ReviewSyncEntityHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReviewSyncEntityHandler(ratingService, placeIdentityService, new ObjectMapper());
    }

    @Test
    void applyPushedChange_WhenEntityUserDoesNotMatchAuthenticatedUser_Throws() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("place-1:attacker");
        pushed.setOperation("CREATE");
        pushed.setPayload("{\"stars\":5,\"comment\":\"ok\"}");

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
        pushed.setPayload("{\"placeId\":\"place-2\",\"userId\":\"attacker\",\"stars\":4,\"comment\":\"nice\"}");

        ReviewResponseDTO persisted = new ReviewResponseDTO(
            "r1",
            "place-1",
            "u1",
            "Anonymous",
            4,
            "nice",
            1764547200000L);
        when(ratingService.saveOrUpdateReview(4, "nice", "u1", "place-1", "place-1", 2L,
            null, null, null, null, null))
            .thenReturn(persisted);

        String payload = handler.applyPushedChange(pushed, "u1", 2L);

        verify(ratingService).saveOrUpdateReview(
                eq(4),
                eq("nice"),
                eq("u1"),
                eq("place-1"),
                eq("place-1"),
                eq(2L),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null));
        assertTrue(payload.contains("\"placeId\":\"place-1\""));
        assertFalse(payload.contains("\"placeId\":\"place-2\""));
        assertTrue(payload.contains("\"userId\":\"u1\""));
    }

    @Test
    void applyPushedChange_WhenMetadataResolves_UsesResolvedPlaceId() {
        SyncChange pushed = new SyncChange();
        pushed.setEntityId("GHOST_ID:u1");
        pushed.setOperation("CREATE");
        pushed.setPayload("{\"stars\":5,\"comment\":\"ok\",\"externalSource\":\"GOOGLE_MAPS\",\"externalId\":\"ext-1\",\"lat\":10.0,\"lng\":20.0,\"placeName\":\"Cafe\"}");

        when(placeIdentityService.resolveInternalPlaceId("GOOGLE_MAPS", "ext-1", 10.0, 20.0, "Cafe"))
                .thenReturn("REAL_ID");

        ReviewResponseDTO persisted = new ReviewResponseDTO(
                "r1",
                "REAL_ID",
                "u1",
                "Anonymous",
                5,
                "ok",
                1764547200000L);
        when(ratingService.saveOrUpdateReview(5, "ok", "u1", "GHOST_ID", "REAL_ID", 7L,
                "GOOGLE_MAPS", "ext-1", 10.0, 20.0, "Cafe"))
                .thenReturn(persisted);

        String payload = handler.applyPushedChange(pushed, "u1", 7L);

        verify(ratingService).saveOrUpdateReview(
                eq(5),
                eq("ok"),
                eq("u1"),
                eq("GHOST_ID"),
                eq("REAL_ID"),
                eq(7L),
                eq("GOOGLE_MAPS"),
                eq("ext-1"),
                eq(10.0),
                eq(20.0),
                eq("Cafe"));
        assertTrue(payload.contains("\"placeId\":\"REAL_ID\""));
    }
}
