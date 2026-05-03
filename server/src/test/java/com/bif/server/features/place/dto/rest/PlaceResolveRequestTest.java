package com.bif.server.features.place.dto.rest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlaceResolveRequestTest {

    @Test
    void constructor_WhenValuesAreValid_DoesNotThrow() {
        assertDoesNotThrow(() -> new PlaceResolveRequest(
                "google",
                "ext-1",
                10.5,
                106.7,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenExternalSourceIsBlank_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                " ",
                "ext-1",
                10.5,
                106.7,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenExternalIdIsBlank_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                " ",
                10.5,
                106.7,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenNameIsBlank_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                "ext-1",
                10.5,
                106.7,
                " "
        ));
    }

    @Test
    void constructor_WhenLatitudeOutOfRange_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                "ext-1",
                90.1,
                106.7,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenLongitudeOutOfRange_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                "ext-1",
                10.5,
                180.1,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenLatitudeIsNull_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                "ext-1",
                null,
                106.7,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenLongitudeIsNull_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                "ext-1",
                10.5,
                null,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenLatitudeIsNotFinite_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                "ext-1",
                Double.NaN,
                106.7,
                "Coffee Shop"
        ));
    }

    @Test
    void constructor_WhenLongitudeIsNotFinite_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new PlaceResolveRequest(
                "google",
                "ext-1",
                10.5,
                Double.POSITIVE_INFINITY,
                "Coffee Shop"
        ));
    }
}
