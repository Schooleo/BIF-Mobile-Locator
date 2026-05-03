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

public class EmbeddedBRouterEngineTest {

    private EmbeddedBRouterEngine engine;

    @Before
    public void setUp() {
        engine = new EmbeddedBRouterEngine();
    }

    @Test
    public void isReady_missingDirectory_returnsFalse() {
        File missing = new File("does-not-exist");
        assertFalse(engine.isReady(missing));
    }

    @Test
    public void route_lessThanTwoWaypoints_returnsNull() {
        File missing = new File("does-not-exist");
        Route result = engine.route(List.of(new Location(10.7, 106.7)), "car-fast.brf", missing);
        assertNull(result);
    }

    @Test
    public void route_invalidMapDirectory_returnsNull() {
        File missing = new File("does-not-exist");
        Route result = engine.route(
                List.of(new Location(10.7, 106.7), new Location(10.8, 106.8)),
                "car-fast.brf",
                missing);
        assertNull(result);
    }

    @Test
    public void resolveProfileFileName_aliasesMapToExpectedProfiles() throws Exception {
        Method method = EmbeddedBRouterEngine.class
                .getDeclaredMethod("resolveProfileFileName", String.class);
        method.setAccessible(true);

        assertEquals("car-fast.brf", method.invoke(engine, "driving"));
        assertEquals("bicycle.brf", method.invoke(engine, "cycling"));
        assertEquals("foot.brf", method.invoke(engine, "walking"));
        assertEquals("car-fast.brf", method.invoke(engine, "unknown"));
        assertEquals("car-fast.brf", method.invoke(engine, ""));
    }
}
