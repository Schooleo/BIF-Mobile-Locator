package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.bif.server.features.place.models.Place;

public class PlaceSearchIndexSyncServiceDefaultTest {

    @Test
    void defaultBatchUpsert_callsUpsertForEach() {
        AtomicInteger called = new AtomicInteger(0);

        PlaceSearchIndexSyncService svc = new PlaceSearchIndexSyncService() {
            @Override
            public void upsert(Place place) {
                called.incrementAndGet();
            }

            @Override
            public void deleteById(String placeId) {
                // not used
            }
        };

        List<Place> items = List.of(new Place(), new Place(), new Place());
        int count = svc.batchUpsert(items);

        assertEquals(3, count);
        assertEquals(3, called.get());
    }

    @Test
    void defaultEnsureCollectionExists_noop() {
        PlaceSearchIndexSyncService svc = new PlaceSearchIndexSyncService() {
            @Override
            public void upsert(Place place) {}

            @Override
            public void deleteById(String placeId) {}
        };

        // ensureCollectionExists default is a no-op; calling should not throw
        svc.ensureCollectionExists();
    }
}
