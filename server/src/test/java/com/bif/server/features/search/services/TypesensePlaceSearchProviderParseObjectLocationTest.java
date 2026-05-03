package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypesensePlaceSearchProviderParseObjectLocationTest {

    @Test
    void parsePlaces_parsesObjectLocationVariants() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        ObjectMapper mapper = new ObjectMapper();
        TypesensePlaceSearchProvider provider = new TypesensePlaceSearchProvider(props, mapper);

        String payload1 = "{\"hits\":[{\"document\":{\"id\":\"o1\",\"name\":\"Obj1\",\"location\":{\"latitude\":11.1,\"longitude\":22.2}}}]}";
        String payload2 = "{\"hits\":[{\"document\":{\"id\":\"o2\",\"name\":\"Obj2\",\"location\":{\"lat\":33.3,\"lng\":44.4}}}]}";
        String payload3 = "{\"hits\":[{\"document\":{\"id\":\"o3\",\"name\":\"Obj3\",\"location\":{\"lat\":55.5,\"lon\":66.6}}}]}";

        Method parse = TypesensePlaceSearchProvider.class.getDeclaredMethod("parsePlaces", String.class);
        parse.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Place> p1 = (List<Place>) parse.invoke(provider, payload1);
        @SuppressWarnings("unchecked")
        List<Place> p2 = (List<Place>) parse.invoke(provider, payload2);
        @SuppressWarnings("unchecked")
        List<Place> p3 = (List<Place>) parse.invoke(provider, payload3);

        assertEquals(1, p1.size());
        assertNotNull(p1.get(0).getLocation());
        assertEquals(11.1, p1.get(0).getLocation().getLatitude(), 0.0001);
        assertEquals(22.2, p1.get(0).getLocation().getLongitude(), 0.0001);

        assertEquals(1, p2.size());
        assertNotNull(p2.get(0).getLocation());
        assertEquals(33.3, p2.get(0).getLocation().getLatitude(), 0.0001);
        assertEquals(44.4, p2.get(0).getLocation().getLongitude(), 0.0001);

        assertEquals(1, p3.size());
        assertNotNull(p3.get(0).getLocation());
        assertEquals(55.5, p3.get(0).getLocation().getLatitude(), 0.0001);
        assertEquals(66.6, p3.get(0).getLocation().getLongitude(), 0.0001);
    }
}
