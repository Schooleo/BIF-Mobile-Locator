package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypesensePlaceSearchProviderNetworkTest {

    @Test
    void search_handles200ResponseAndParsesPlaces() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("key");
        props.setHost("localhost");
        props.setPort(8108);

        ObjectMapper mapper = new ObjectMapper();

        HttpClient mockClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> mockResp = Mockito.mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body())
                .thenReturn("{\"hits\":[{\"document\":{\"id\":\"n1\",\"name\":\"NetPlace\",\"location\":[1.2,3.4]}}]}");

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResp);

        TypesensePlaceSearchProvider provider = new TypesensePlaceSearchProvider(props, mapper, mockClient);
        List<Place> results = provider.search("x");

        assertEquals(1, results.size());
        assertEquals("n1", results.get(0).getId());
        assertEquals("NetPlace", results.get(0).getName());
        assertEquals(1.2, results.get(0).getLocation().getLatitude(), 0.0001);
    }
}
