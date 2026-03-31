package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypesensePlaceSearchProviderParseTest {

    @Test
    void parsePlaces_parsesDocumentAndLocation() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        ObjectMapper mapper = new ObjectMapper();
        TypesensePlaceSearchProvider provider = new TypesensePlaceSearchProvider(props, mapper);

        String payload = "{\"hits\":[{\"document\":{\"id\":\"p1\",\"name\":\"Cafe\",\"address\":\"123 St\",\"rating\":4.2,\"placeSource\":\"typesense\",\"persistedByAction\":\"a\",\"persistedByUserId\":\"u\",\"location\":[10.5,20.25]}}]}";

        Method parse = TypesensePlaceSearchProvider.class.getDeclaredMethod("parsePlaces", String.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Place> places = (List<Place>) parse.invoke(provider, payload);

        assertNotNull(places);
        assertEquals(1, places.size());
        Place p = places.get(0);
        assertEquals("p1", p.getId());
        assertEquals("Cafe", p.getName());
        assertEquals("123 St", p.getAddress());
        assertEquals(4.2, p.getRating(), 0.001);
        assertNotNull(p.getLocation());
        assertEquals(10.5, p.getLocation().getLatitude(), 0.0001);
        assertEquals(20.25, p.getLocation().getLongitude(), 0.0001);
    }
}
