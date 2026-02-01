package com.bif.locator.data.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocationTest {

    @Test
    public void constructor_setsFieldsCorrectly() {
        String id = "1";
        String name = "Test Loc";
        String description = "Desc";
        double lat = 10.0;
        double lng = 20.0;
        String type = "Park";

        Location location = new Location(id, name, description, lat, lng, type);

        assertEquals("Name should be set", name, location.getName());
        assertEquals("Type should be set", type, location.getType());
    }
}
