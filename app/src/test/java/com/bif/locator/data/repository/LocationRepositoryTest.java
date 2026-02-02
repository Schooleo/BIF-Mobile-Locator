package com.bif.locator.data.repository;

import org.junit.Test;
import static org.junit.Assert.*;

public class LocationRepositoryTest {

    @Test
    public void constructor_canInstantiate() {
        LocationRepository repo = new LocationRepository(null);
        assertNotNull("Repository should be instantiated", repo);
    }
}
