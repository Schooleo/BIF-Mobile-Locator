package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.net.URI;

import org.junit.jupiter.api.Test;

import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypesensePlaceSearchProviderBuildUriTest {

    @Test
    void buildSearchUri_encodesQueryAndCollection() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setProtocol("https");
        props.setHost("example.com");
        props.setPort(1234);
        props.setPlacesCollection("my places");

        TypesensePlaceSearchProvider provider = new TypesensePlaceSearchProvider(props, new ObjectMapper());

        Method m = TypesensePlaceSearchProvider.class.getDeclaredMethod("buildSearchUri", String.class);
        m.setAccessible(true);

        URI uri = (URI) m.invoke(provider, "a+b &/?");
        String s = uri.toString();

        assertTrue(s.startsWith("https://example.com:1234/collections/"));
        // collection should be URL-encoded (space -> +)
        assertTrue(s.contains("collections/my+places/documents/search?q="));
        // query should be URL-encoded
        assertTrue(s.contains("q=a%2Bb+%26%2F%3F") || s.contains("q=a%2Bb+%2526%2F%3F") || s.contains("q=a%2Bb+%26%2F%3F"));
    }
}
