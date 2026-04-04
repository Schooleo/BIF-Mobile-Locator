package com.bif.server.features.ai.services;

import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.services.MongoPlaceSearchProvider;
import com.bif.server.features.search.services.TypesensePlaceSearchProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiPlaceGroundingServiceTest {

    @Mock
    private MongoPlaceSearchProvider mongoPlaceSearchProvider;

    @Mock
    private TypesensePlaceSearchProvider typesensePlaceSearchProvider;

    @Mock
    private PlaceRepository placeRepository;

    @Test
    void ground_UsesTypesenseTagAwareQueryWhenConfigured() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "typesense",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );
        Place place = place("p1", "Cafe");
        when(typesensePlaceSearchProvider.search(
                "coffee cafe cozy quiet romantic",
                "name,address,tags",
                8
        )).thenReturn(List.of(place));
        when(typesensePlaceSearchProvider.search(
                "cafe",
                "tags,name,address",
                8
        )).thenReturn(List.of());

        List<Place> result = service.ground(
                new PlaceSearchExtraction(List.of("coffee"), "cafe", "cozy")
        );

        assertEquals(1, result.size());
        assertEquals("p1", result.getFirst().getId());
        verify(typesensePlaceSearchProvider).search(
                "coffee cafe cozy quiet romantic",
                "name,address,tags",
                8
        );
    }

    @Test
    void ground_UsesMongoSearchAndTagHintsWhenConfigured() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );
        Place textMatch = place("p1", "Coffee House");
        Place tagMatch = place("p2", "Late Night Cafe");
        when(mongoPlaceSearchProvider.search("coffee cafe"))
                .thenReturn(List.of(textMatch));
        when(mongoPlaceSearchProvider.search("coffee"))
                .thenReturn(List.of(textMatch));
        when(placeRepository.findByTagsContaining("cafe"))
                .thenReturn(List.of(tagMatch));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(List.of("coffee"), "cafe", null)
        );

        assertEquals(2, result.size());
        assertEquals("p1", result.get(0).getId());
        assertEquals("p2", result.get(1).getId());
        verify(placeRepository).findByTagsContaining("cafe");
    }

    @Test
    void ground_UnknownVibeDegradesSafely() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );
        when(mongoPlaceSearchProvider.search("coffee"))
                .thenReturn(List.of(place("p1", "Coffee House")));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(List.of("coffee"), null, "unknown vibe")
        );

        assertEquals(1, result.size());
        verify(placeRepository, never()).findByTagsContaining("unknown-vibe");
    }

    private Place place(String id, String name) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        return place;
    }
}
