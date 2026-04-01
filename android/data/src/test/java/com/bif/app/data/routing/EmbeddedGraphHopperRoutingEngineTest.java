package com.bif.app.data.routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Route;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

public class EmbeddedGraphHopperRoutingEngineTest {

    private EmbeddedGraphHopperRoutingEngine engine;

    @Before
    public void setUp() {
        engine = new EmbeddedGraphHopperRoutingEngine();
    }

    @Test
    public void isReady_missingFile_returnsFalse() {
        File missing = new File("does-not-exist.osm.pbf");
        assertFalse(engine.isReady(missing));
    }

    @Test
    public void route_lessThanTwoWaypoints_returnsNull() {
        File missing = new File("does-not-exist.osm.pbf");
        Route result = engine.route(List.of(new Location(10.7, 106.7)), "car", missing);
        assertNull(result);
    }

    @Test
    public void route_invalidMapFile_returnsNull() {
        File missing = new File("does-not-exist.osm.pbf");
        Route result = engine.route(
                List.of(new Location(10.7, 106.7), new Location(10.8, 106.8)),
                "car",
                missing);
        assertNull(result);
    }

    @Test
    public void resolveProfile_aliasesMapToSupportedProfiles() throws Exception {
        Method method = EmbeddedGraphHopperRoutingEngine.class
                .getDeclaredMethod("resolveProfile", String.class);
        method.setAccessible(true);

        assertEquals("car", method.invoke(engine, "driving"));
        assertEquals("bike", method.invoke(engine, "cycling"));
        assertEquals("foot", method.invoke(engine, "walking"));
        assertEquals("car", method.invoke(engine, "unknown"));
        assertEquals("car", method.invoke(engine, ""));
    }
}
