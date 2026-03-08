package com.bif.app.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaceTest {

    @Test
    public void place_initialization_setsValuesCorrectly() {
        Location location = new Location();
        location.latitude = 10.0;
        location.longitude = 20.0;
        Place place = new Place("1", "Coffee Shop", "456 Side St", 4.5, location);

        assertEquals("1", place.id);
        assertEquals("Coffee Shop", place.name);
        assertEquals("456 Side St", place.address);
        assertEquals(4.5, place.rating, 0.0);
        assertNotNull(place.location);
        assertEquals(10.0, place.location.latitude, 0.0);
        assertEquals(20.0, place.location.longitude, 0.0);
    }
}
