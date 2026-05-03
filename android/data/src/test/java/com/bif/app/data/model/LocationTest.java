package com.bif.app.data.model;

import com.bif.app.domain.model.Location;
import org.junit.Assert;
import org.junit.Test;

public class LocationTest {

    @Test
    public void fields_areSetCorrectly() {
        double lat = 10.0;
        double lng = 20.0;

        Location location = new Location();
        location.latitude = lat;
        location.longitude = lng;

        Assert.assertEquals("Latitude should be set", lat, location.latitude, 0.001);
        Assert.assertEquals("Longitude should be set", lng, location.longitude, 0.001);
    }
}
