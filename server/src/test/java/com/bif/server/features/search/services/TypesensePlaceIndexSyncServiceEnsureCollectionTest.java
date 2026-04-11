package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TypesensePlaceIndexSyncServiceEnsureCollectionTest {

    @Test
    void ensureCollectionExists_waitsForHealthAndCreatesCollectionWhenMissing() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("key");
        props.setHost("localhost");
        props.setPort(8108);

        ObjectMapper mapper = new ObjectMapper();
        HttpClient mockClient = Mockito.mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> respHealth = Mockito.mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> respGet = Mockito.mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> respPost = Mockito.mock(HttpResponse.class);

        when(respHealth.statusCode()).thenReturn(200);
        when(respGet.statusCode()).thenReturn(404);
        when(respPost.statusCode()).thenReturn(201);

        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(respHealth)
                .thenReturn(respGet)
                .thenReturn(respPost);

        TypesensePlaceIndexSyncService svc = new TypesensePlaceIndexSyncService(props, mapper, mockClient);

        svc.ensureCollectionExists();

        // expected at least the three calls: health, get collection, create collection
        Mockito.verify(mockClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void ensureCollectionExists_invalidProtocolFallsBackToHttp() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("key");
        props.setHost("localhost");
        props.setPort(8108);
        props.setProtocol("http1985");

        ObjectMapper mapper = new ObjectMapper();
        HttpClient mockClient = Mockito.mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        HttpResponse<String> respHealth = Mockito.mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> respGet = Mockito.mock(HttpResponse.class);

        when(respHealth.statusCode()).thenReturn(200);
        when(respGet.statusCode()).thenReturn(200);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(respHealth)
                .thenReturn(respGet);

        TypesensePlaceIndexSyncService svc = new TypesensePlaceIndexSyncService(props, mapper, mockClient);

        assertDoesNotThrow(svc::ensureCollectionExists);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(mockClient, times(2)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("http", requestCaptor.getAllValues().get(0).uri().getScheme());
    }
}
