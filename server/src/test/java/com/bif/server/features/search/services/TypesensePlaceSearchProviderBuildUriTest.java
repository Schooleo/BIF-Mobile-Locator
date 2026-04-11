package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.net.URI;

import org.junit.jupiter.api.Test;

import com.bif.server.features.search.config.TypesenseProperties;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypesensePlaceSearchProviderBuildUriTest {

    private TypesensePlaceSearchProvider createProvider() {
        TypesenseProperties props = new TypesenseProperties();
        props.setProtocol("https");
        props.setHost("example.com");
        props.setPort(1234);
        props.setPlacesCollection("my places");
        return new TypesensePlaceSearchProvider(props, new ObjectMapper());
    }

    @Test
    void buildSearchUri_encodesQueryAndCollection() throws Exception {
        TypesensePlaceSearchProvider provider = createProvider();

        Method m = TypesensePlaceSearchProvider.class.getDeclaredMethod("buildSearchUri", String.class);
        m.setAccessible(true);

        URI uri = (URI) m.invoke(provider, "a+b &/?");
        String s = uri.toString();

        assertTrue(s.startsWith("https://example.com:1234/collections/"));
        assertTrue(s.contains("collections/my+places/documents/search?q="));
        assertTrue(s.contains("q=a%2Bb+%26%2F%3F") || s.contains("q=a%2Bb+%2526%2F%3F"));
        assertTrue(s.contains("per_page=15"));
        assertTrue(s.contains("query_by_weights=3,1"));
        assertTrue(s.contains("drop_tokens_threshold=0"));
        assertTrue(!s.contains("remove_extra_tokens="));
        assertTrue(s.contains("num_typos=1"));
        assertTrue(s.contains("sort_by=_text_match%3Adesc%2Crating%3Adesc"));
        assertFalse(s.contains("filter_by="));
    }

    @Test
    void buildSearchUri_includesEncodedGeoParamsWhenCoordinatesPresent() throws Exception {
        TypesensePlaceSearchProvider provider = createProvider();

        Method m = TypesensePlaceSearchProvider.class.getDeclaredMethod(
                "buildSearchUri",
                PlaceSearchRequestDTO.class,
                String.class);
        m.setAccessible(true);

        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
        request.setQuery("cafe");
        request.setLatitude(21.0278);
        request.setLongitude(105.8342);
        request.setPerPage(7);

        URI uri = (URI) m.invoke(provider, request, "name,address");
        String s = uri.toString();

        assertTrue(s.contains("per_page=7"));
        assertTrue(s.contains("query_by_weights=3,1"));
        assertTrue(s.contains("drop_tokens_threshold=0"));
        assertTrue(!s.contains("remove_extra_tokens="));
        assertTrue(s.contains("num_typos=1"));
        assertTrue(s.contains("filter_by=location%3A%2821.0278%2C105.8342%2C25km%29"));
        assertTrue(s.contains("sort_by=location%2821.0278%2C105.8342%29%3Aasc%2C_text_match%3Adesc%2Crating%3Adesc"));
    }

    @Test
    void buildSearchUri_keepsGeoRadiusWhenCoordinatesArePresentEvenForLongQueries() throws Exception {
        TypesensePlaceSearchProvider provider = createProvider();

        Method m = TypesensePlaceSearchProvider.class.getDeclaredMethod(
                "buildSearchUri",
                PlaceSearchRequestDTO.class,
                String.class);
        m.setAccessible(true);

        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();
        request.setQuery("Highlands Phan Thiet");
        request.setLatitude(21.0278);
        request.setLongitude(105.8342);

        URI uri = (URI) m.invoke(provider, request, "name,address");
        String s = uri.toString();

        assertTrue(s.contains("per_page=15"));
        assertTrue(s.contains("query_by_weights=3,1"));
        assertTrue(s.contains("drop_tokens_threshold=0"));
        assertTrue(!s.contains("remove_extra_tokens="));
        assertTrue(s.contains("num_typos=1"));
        assertTrue(s.contains("sort_by=location%2821.0278%2C105.8342%29%3Aasc%2C_text_match%3Adesc%2Crating%3Adesc"));
        assertTrue(s.contains("filter_by=location%3A%2821.0278%2C105.8342%2C25km%29"));
    }
}
