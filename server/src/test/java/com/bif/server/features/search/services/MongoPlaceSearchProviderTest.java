package com.bif.server.features.search.services;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.bif.server.common.models.Location;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;

public class MongoPlaceSearchProviderTest {

    @Test
    void search_returnsEmptyForBlankQuery() {
        PlaceRepository repo = Mockito.mock(PlaceRepository.class);
        MongoPlaceSearchProvider provider = new MongoPlaceSearchProvider(repo);

        List<Place> result1 = provider.search((String) null);
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

    @Test
    void search_withCoordinates_prioritizesNearestPlace() {
        PlaceRepository repo = Mockito.mock(PlaceRepository.class);
        Place far = buildPlace("far", "Far Place", 21.0278, 105.8342);
        Place medium = buildPlace("medium", "Medium Place", 10.88, 106.78);
        Place near = buildPlace("near", "Near Place", 10.7769, 106.7009);

        when(repo.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(eq("coffee"), eq("coffee")))
                .thenReturn(List.of(far, medium, near));

        MongoPlaceSearchProvider provider = new MongoPlaceSearchProvider(repo);
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO("coffee", 10.7765, 106.7004, 10);

        List<Place> results = provider.search(request);
        List<String> orderedIds = results.stream().map(Place::getId).collect(Collectors.toList());

        assertEquals(List.of("near", "medium", "far"), orderedIds);
    }

    @Test
    void search_respectsPerPageAfterRanking() {
        PlaceRepository repo = Mockito.mock(PlaceRepository.class);
        Place p1 = buildPlace("p1", "P1", 10.7766, 106.7005);
        Place p2 = buildPlace("p2", "P2", 10.7767, 106.7006);
        Place p3 = buildPlace("p3", "P3", 10.7768, 106.7007);

        when(repo.findByNameContainingIgnoreCaseOrAddressContainingIgnoreCase(eq("coffee"), eq("coffee")))
                .thenReturn(new ArrayList<>(List.of(p1, p2, p3)));

        MongoPlaceSearchProvider provider = new MongoPlaceSearchProvider(repo);
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO("coffee", 10.7765, 106.7004, 2);

        List<Place> results = provider.search(request);

        assertEquals(2, results.size());
        assertEquals("p1", results.get(0).getId());
        assertEquals("p2", results.get(1).getId());
    }

    private Place buildPlace(String id, String name, double latitude, double longitude) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        place.setLocation(new Location(latitude, longitude));
        return place;
    }
}
