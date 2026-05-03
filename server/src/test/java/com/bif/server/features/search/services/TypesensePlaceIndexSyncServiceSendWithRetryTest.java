package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypesensePlaceIndexSyncServiceSendWithRetryTest {

    @Test
    void sendWithRetry_retriesOn503_thenReturnsSuccessfulResponse() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("key");
        ObjectMapper mapper = new ObjectMapper();
        PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);

        HttpClient mockClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> r1 = Mockito.mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> r2 = Mockito.mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> r3 = Mockito.mock(HttpResponse.class);

        when(r1.statusCode()).thenReturn(503);
        when(r2.statusCode()).thenReturn(503);
        when(r3.statusCode()).thenReturn(200);
        when(r3.body()).thenReturn("ok");

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r1)
                .thenReturn(r2)
                .thenReturn(r3);

        TypesensePlaceIndexSyncService svc = new TypesensePlaceIndexSyncService(
            props, mapper, mockClient, placeRepository);

        Method m = TypesensePlaceIndexSyncService.class.getDeclaredMethod("sendWithRetry", HttpRequest.class, String.class);
        m.setAccessible(true);

        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("http://localhost/"))
                .GET().build();

        @SuppressWarnings("unchecked")
        HttpResponse<String> resp = (HttpResponse<String>) m.invoke(svc, req, "test");

        assertEquals(200, resp.statusCode());
        Mockito.verify(mockClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
