package com.bif.server.features.search.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bif.server.features.place.models.Place;

@ExtendWith(MockitoExtension.class)
class ConfigurablePlaceSearchProviderTest {

    @Mock
    private MongoPlaceSearchProvider mongoPlaceSearchProvider;

    @Mock
    private TypesensePlaceSearchProvider typesensePlaceSearchProvider;

    @Test
    void search_UsesMongoWhenConfigured() {
        ConfigurablePlaceSearchProvider provider =
                new ConfigurablePlaceSearchProvider(
                        "mongo",
                        mongoPlaceSearchProvider,
                        typesensePlaceSearchProvider);
        Place place = new Place();
        when(mongoPlaceSearchProvider.search("cafe")).thenReturn(List.of(place));

        List<Place> result = provider.search("cafe");

        assertEquals(1, result.size());
        verify(mongoPlaceSearchProvider).search("cafe");
    }

    @Test
    void search_UsesTypesenseWhenConfigured() {
        ConfigurablePlaceSearchProvider provider =
                new ConfigurablePlaceSearchProvider(
                        "typesense",
                        mongoPlaceSearchProvider,
                        typesensePlaceSearchProvider);
        Place place = new Place();
        when(typesensePlaceSearchProvider.search("cafe"))
                .thenReturn(List.of(place));

        List<Place> result = provider.search("cafe");

        assertEquals(1, result.size());
        verify(typesensePlaceSearchProvider).search("cafe");
    }

    @Test
    void search_UnknownProviderFallsBackToMongo() {
        ConfigurablePlaceSearchProvider provider =
                new ConfigurablePlaceSearchProvider(
                        "unknown",
                        mongoPlaceSearchProvider,
                        typesensePlaceSearchProvider);
        when(mongoPlaceSearchProvider.search("museum"))
                .thenReturn(List.of(new Place()));

        List<Place> result = provider.search("museum");

        assertEquals(1, result.size());
        verify(mongoPlaceSearchProvider).search("museum");
    }
}
