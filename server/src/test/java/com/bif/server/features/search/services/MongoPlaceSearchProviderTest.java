package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;

public class MongoPlaceSearchProviderTest {

    @Test
    void search_returnsEmptyForBlankQuery() {
        PlaceRepository repo = Mockito.mock(PlaceRepository.class);
        MongoPlaceSearchProvider provider = new MongoPlaceSearchProvider(repo);

        List<Place> result1 = provider.search(null);
        List<Place> result2 = provider.search("");
        List<Place> result3 = provider.search("   ");

        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
        assertTrue(result3.isEmpty());
    }

    @Test
    void search_delegatesToRepository() {
        PlaceRepository repo = Mockito.mock(PlaceRepository.class);
        Place p = new Place();
        p.setId("p1");
        when(repo.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(eq("coffee"), eq("coffee")))
                .thenReturn(List.of(p));

        MongoPlaceSearchProvider provider = new MongoPlaceSearchProvider(repo);
        List<Place> results = provider.search("coffee");

        assertEquals(1, results.size());
        assertEquals("p1", results.get(0).getId());
    }
}
