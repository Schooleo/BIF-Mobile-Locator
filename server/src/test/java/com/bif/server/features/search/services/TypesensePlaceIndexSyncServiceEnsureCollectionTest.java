package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Flow;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.bif.server.features.place.repositories.PlaceRepository;
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
        PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);

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

        TypesensePlaceIndexSyncService svc = new TypesensePlaceIndexSyncService(
            props, mapper, mockClient, placeRepository);

        svc.ensureCollectionExists();

        // expected at least the three calls: health, get collection, create collection
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        Mockito.verify(mockClient, times(3)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        List<HttpRequest> requests = requestCaptor.getAllValues();
        String schemaBody = readBody(requests.get(2).bodyPublisher());
        assertEquals("POST", requests.get(2).method());
        org.junit.jupiter.api.Assertions.assertTrue(schemaBody.contains("\"location\""));
        org.junit.jupiter.api.Assertions.assertTrue(schemaBody.contains("\"city\""));
        org.junit.jupiter.api.Assertions.assertTrue(schemaBody.contains("\"district\""));
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

    private String readBody(Optional<HttpRequest.BodyPublisher> bodyPublisherOptional) throws Exception {
        if (bodyPublisherOptional == null || bodyPublisherOptional.isEmpty()) {
            return "";
        }
        HttpRequest.BodyPublisher bodyPublisher = bodyPublisherOptional.get();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> payloadRef = new AtomicReference<>(new byte[0]);

        bodyPublisher.subscribe(new Flow.Subscriber<>() {
            private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.write(bytes, 0, bytes.length);
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                payloadRef.set(out.toByteArray());
                latch.countDown();
            }
        });

        latch.await(2, TimeUnit.SECONDS);
        return new String(payloadRef.get(), StandardCharsets.UTF_8);
    }
}
