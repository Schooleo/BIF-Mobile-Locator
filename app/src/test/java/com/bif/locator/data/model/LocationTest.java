package com.bif.locator.data.model;

import com.bif.locator.domain.model.Location;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationTest {

    @Test
    public void fields_areSetCorrectly() {
        double lat = 10.0;
        double lng = 20.0;

        Location location = new Location();
        location.latitude = lat;
        location.longitude = lng;

        assertEquals("Latitude should be set", lat, location.latitude, 0.001);
        assertEquals("Longitude should be set", lng, location.longitude, 0.001);
    }
}
