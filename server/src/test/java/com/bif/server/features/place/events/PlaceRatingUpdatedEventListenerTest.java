package com.bif.server.features.place.events;

import com.bif.server.features.search.config.TypesenseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.typesense.api.Client;
import org.typesense.api.Collection;
import org.typesense.api.Document;
import org.typesense.api.exceptions.ServiceUnavailable;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceRatingUpdatedEventListenerTest {

    @Mock
    private Client typesenseClient;

    @Mock
    private Collection typesenseCollection;

    @Mock
    private Document typesenseDocument;

    private PlaceRatingUpdatedEventListener listener;

    @BeforeEach
    void setUp() throws Exception {
        TypesenseProperties properties = new TypesenseProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setPlacesCollection("places");

        listener = new PlaceRatingUpdatedEventListener(typesenseClient, properties);

        when(typesenseClient.collections("places")).thenReturn(typesenseCollection);
        when(typesenseCollection.documents("p1")).thenReturn(typesenseDocument);
    }

    @Test
    void onPlaceRatingUpdated_UpdatesOnlyRatingAndReviewCount() throws Exception {
        PlaceRatingUpdatedEvent event = new PlaceRatingUpdatedEvent("p1", 4.6, 12);

        new SyncTaskExecutor().execute(() -> listener.onPlaceRatingUpdated(event));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor =
            (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(typesenseDocument, times(1)).update(payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals(Set.of("rating", "reviewCount"), payload.keySet());
        assertEquals(4.6, payload.get("rating"));
        assertEquals(12, payload.get("reviewCount"));
    }

    @Test
    void onPlaceRatingUpdated_WhenTypesenseThrows_DoesNotPropagate() throws Exception {
        when(typesenseDocument.update(anyMap())).thenThrow(new RuntimeException("typesense failed"));

        PlaceRatingUpdatedEvent event = new PlaceRatingUpdatedEvent("p1", 4.2, 9);

        assertDoesNotThrow(() -> new SyncTaskExecutor().execute(() -> listener.onPlaceRatingUpdated(event)));
        verify(typesenseDocument, times(1)).update(anyMap());
    }

    @Test
    void onPlaceRatingUpdated_WhenServiceUnavailable_RetriesThenSucceeds() throws Exception {
        when(typesenseDocument.update(anyMap()))
                .thenThrow(new ServiceUnavailable("busy", 503))
                .thenReturn(Map.of("id", "p1"));

        PlaceRatingUpdatedEvent event = new PlaceRatingUpdatedEvent("p1", 4.7, 20);

        assertDoesNotThrow(() -> new SyncTaskExecutor().execute(() -> listener.onPlaceRatingUpdated(event)));
        verify(typesenseDocument, times(2)).update(anyMap());
    }
}
