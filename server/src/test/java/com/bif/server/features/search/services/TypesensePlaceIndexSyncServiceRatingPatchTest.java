package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.config.TypesenseProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

class TypesensePlaceIndexSyncServiceRatingPatchTest {

        @Test
        void upsert_serializesLocationAsLatLngArray() throws Exception {
                TypesenseProperties props = new TypesenseProperties();
                props.setEnabled(true);
                props.setApiKey("key");
                props.setHost("localhost");
                props.setPort(8108);

                HttpClient httpClient = Mockito.mock(HttpClient.class);
                @SuppressWarnings("unchecked")
                HttpResponse<String> upsertResponse = Mockito.mock(HttpResponse.class);
                when(upsertResponse.statusCode()).thenReturn(201);
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                                .thenReturn(upsertResponse);

                PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
                TypesensePlaceIndexSyncService service = new TypesensePlaceIndexSyncService(
                                props, new ObjectMapper(), httpClient, placeRepository);

                Place place = new Place();
                place.setId("p-loc");
                place.setName("Bach Khoa");
                place.setLocation(new Location(10.77, 106.69));

                service.upsert(place);

                ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
                verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

                String body = readBody(requestCaptor.getValue());
                assertTrue(body.contains("\"location\":[10.77,106.69]"));
        }

        @Test
        void batchUpsert_serializesLocationAsLatLngArray() throws Exception {
                TypesenseProperties props = new TypesenseProperties();
                props.setEnabled(true);
                props.setApiKey("key");
                props.setHost("localhost");
                props.setPort(8108);

                HttpClient httpClient = Mockito.mock(HttpClient.class);
                @SuppressWarnings("unchecked")
                HttpResponse<String> importResponse = Mockito.mock(HttpResponse.class);
                when(importResponse.statusCode()).thenReturn(200);
                when(importResponse.body()).thenReturn("{\"success\":true}");
                when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                                .thenReturn(importResponse);

                TypesensePlaceIndexSyncService service = new TypesensePlaceIndexSyncService(
                                props, new ObjectMapper(), httpClient, Mockito.mock(PlaceRepository.class));

                Place place = new Place();
                place.setId("p-batch-loc");
                place.setName("Bach Khoa");
                place.setLocation(new Location(10.77, 106.69));

                int imported = service.batchUpsert(java.util.List.of(place));
                assertEquals(1, imported);

                ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
                verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

                String body = readBody(requestCaptor.getValue());
                assertTrue(body.contains("\"location\":[10.77,106.69]"));
        }

    @Test
    void updateRatingOnly_buildsPatchRequestWithEncodedId() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("key");
        props.setHost("localhost");
        props.setPort(8108);

        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> patchResponse = Mockito.mock(HttpResponse.class);
        when(patchResponse.statusCode()).thenReturn(200);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(patchResponse);

        PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);

        TypesensePlaceIndexSyncService service = new TypesensePlaceIndexSyncService(
                props, new ObjectMapper(), httpClient, placeRepository);

        service.updateRatingOnly("place id/1", 4.75, 9);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = requestCaptor.getValue();
        assertEquals("PATCH", sentRequest.method());
        String uri = sentRequest.uri().toString();
        assertTrue(uri.contains("/collections/places/documents/place%20id%2F1"));
    }

    @Test
    void updateRatingOnly_whenPatchReturns404_fallbacksToFullUpsert() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("key");

        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> patchResponse = Mockito.mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> fetchResponse = Mockito.mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> upsertResponse = Mockito.mock(HttpResponse.class);
        when(patchResponse.statusCode()).thenReturn(404);
        when(fetchResponse.statusCode()).thenReturn(404);
        when(upsertResponse.statusCode()).thenReturn(201);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(patchResponse)
                .thenReturn(fetchResponse)
                .thenReturn(upsertResponse);

        PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
        Place place = new Place();
        place.setId("p1");
        place.setName("A");
        place.setAddress("B");
        place.setRating(4.5);
        place.setReviewCount(3);
        place.setLocation(new Location(10.77, 106.69));
        when(placeRepository.findById("p1")).thenReturn(Optional.of(place));

        TypesensePlaceIndexSyncService service = new TypesensePlaceIndexSyncService(
                props, new ObjectMapper(), httpClient, placeRepository);

        service.updateRatingOnly("p1", 4.8, 10);

        verify(placeRepository).findById("p1");
        
        // Verify that the third call (fallback upsert) contains correct rating and reviewCount
        ArgumentCaptor<HttpRequest> upsertRequestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(3)).send(upsertRequestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        
        HttpRequest upsertRequest = upsertRequestCaptor.getAllValues().get(2);
        String upsertBody = readBody(upsertRequest);
        assertTrue(upsertBody.contains("\"rating\":4.8"), 
            "Fallback upsert request body should contain rating 4.8, got: " + upsertBody);
        assertTrue(upsertBody.contains("\"reviewCount\":10"),
            "Fallback upsert request body should contain reviewCount 10, got: " + upsertBody);
    }

    @Test
    void updateRatingOnly_whenPatchReturnsNon404_doesNotFallbackOrThrows() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("key");

        HttpClient httpClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> patchResponse = Mockito.mock(HttpResponse.class);
        when(patchResponse.statusCode()).thenReturn(500);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(patchResponse);

        PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
        Place place = new Place();
        place.setId("p1");
        place.setName("A");
        place.setAddress("B");
        place.setRating(4.5);
        place.setReviewCount(3);
        place.setLocation(new Location(10.77, 106.69));
        when(placeRepository.findById("p1")).thenReturn(Optional.of(place));

        TypesensePlaceIndexSyncService service = new TypesensePlaceIndexSyncService(
                props, new ObjectMapper(), httpClient, placeRepository);

        assertDoesNotThrow(() -> service.updateRatingOnly("p1", 4.8, 10));

        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(placeRepository, never()).findById("p1");
    }

    private String readBody(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                output.write(bytes, 0, bytes.length);
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });

        assertTrue(done.await(2, TimeUnit.SECONDS), "Timed out waiting for body publisher completion");
        Throwable subscriberError = errorRef.get();
        if (subscriberError != null) {
            throw new AssertionError("Body publisher subscriber failed", subscriberError);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
