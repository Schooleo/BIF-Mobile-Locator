package com.bif.server.features.ai.services;

import com.bif.server.features.ai.dto.PlaceSearchExtraction;
import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.bif.server.features.search.services.MongoPlaceSearchProvider;
import com.bif.server.features.search.services.TypesensePlaceSearchProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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
                argThat(request -> request != null
                && "coffee cafe cozy quiet romantic".equals(request.getQuery())
                && request.getLatitude() == null
                && request.getLongitude() == null
                && request.getPerPage() == 16),
                eq("name,address,tags")
        )).thenReturn(List.of(place));
        when(typesensePlaceSearchProvider.search(
                argThat(request -> request != null
                && "cafe".equals(request.getQuery())
                && request.getLatitude() == null
                && request.getLongitude() == null
                && request.getPerPage() == 16),
                eq("tags,name,address")
        )).thenReturn(List.of());

        List<Place> result = service.ground(
                new PlaceSearchExtraction(
                        List.of("coffee cafe cozy quiet romantic"),
                        List.of("coffee"),
                        "cafe",
                        "cozy")
        );

        assertEquals(1, result.size());
        assertEquals("p1", result.getFirst().getId());
        verify(typesensePlaceSearchProvider).search(
                argThat(request -> request != null
                && "coffee cafe cozy quiet romantic".equals(request.getQuery())
                && request.getLatitude() == null
                && request.getLongitude() == null
                && request.getPerPage() == 16),
                eq("name,address,tags")
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
                new PlaceSearchExtraction(
                        List.of("coffee cafe"),
                        List.of("coffee"),
                        "cafe",
                        null)
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
                new PlaceSearchExtraction(
                        List.of("coffee"),
                        List.of("coffee"),
                        null,
                        "unknown vibe")
        );

        assertEquals(1, result.size());
        verify(placeRepository, never()).findByTagsContaining("unknown-vibe");
    }

    @Test
    void ground_PrefersAiPlannedSearchQueriesOverFallbackHints() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );
        when(mongoPlaceSearchProvider.search("rooftop cafe at sunset"))
                .thenReturn(List.of(place("p3", "Sunset Rooftop")));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(
                        List.of("rooftop cafe at sunset"),
                        List.of("coffee"),
                        "cafe",
                        "romantic")
        );

        assertEquals(1, result.size());
        assertEquals("p3", result.getFirst().getId());
        verify(mongoPlaceSearchProvider).search("rooftop cafe at sunset");
    }

    @Test
    void ground_UsesKeywordFallbackSignalsWhenAiSearchQueryMisses() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );

        Place fallbackMatch = place("p7", "District Coffee Spot");
        when(mongoPlaceSearchProvider.search("overly specific zero-hit phrase coffee cafe"))
                .thenReturn(List.of());
        when(mongoPlaceSearchProvider.search("coffee"))
                .thenReturn(List.of(fallbackMatch));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(
                        List.of("overly specific zero-hit phrase"),
                        List.of("coffee"),
                        "cafe",
                        null)
        );

        assertEquals(1, result.size());
        assertEquals("p7", result.getFirst().getId());
        verify(mongoPlaceSearchProvider, atLeastOnce()).search("coffee");
    }

    @Test
    void ground_ExecutesMultipleConcatenatedAiSearchQueries() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );

        when(mongoPlaceSearchProvider.search("rooftop cafe sunset cocktails"))
                .thenReturn(List.of(place("p1", "Skyline Rooftop")));
        when(mongoPlaceSearchProvider.search("rooftop cafe"))
                .thenReturn(List.of(place("p2", "Rooftop Coffee")));
        when(mongoPlaceSearchProvider.search("sunset cocktails"))
                .thenReturn(List.of(place("p3", "Cocktail Terrace")));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(
                        List.of("rooftop cafe", "sunset cocktails"),
                        List.of("coffee"),
                        "cafe",
                        "romantic")
        );

        assertEquals(3, result.size());
        List<String> ids = result.stream().map(Place::getId).toList();
        assertTrue(ids.contains("p1"));
        assertTrue(ids.contains("p2"));
        assertTrue(ids.contains("p3"));

        verify(mongoPlaceSearchProvider).search("rooftop cafe sunset cocktails");
        verify(mongoPlaceSearchProvider).search("rooftop cafe");
        verify(mongoPlaceSearchProvider).search("sunset cocktails");
        verify(placeRepository).findByTagsContaining("cafe");
    }

    @Test
    void ground_PrioritizesAddressCityMatchOverNameOnlyMatch() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );

        Place nameOnlyMatch = place("p1", "Hanoi Souvenir Superstore");
        nameOnlyMatch.setAddress("123 Beach Street, Da Nang");
        nameOnlyMatch.setRating(5.0);
        nameOnlyMatch.setReviewCount(2400);

        Place addressMatch = place("p2", "Hoan Kiem Lake");
        addressMatch.setAddress("Hoan Kiem District, Ha Noi");
        addressMatch.setRating(4.3);
        addressMatch.setReviewCount(350);

        when(mongoPlaceSearchProvider.search("ha noi major attractions"))
                .thenReturn(List.of(nameOnlyMatch, addressMatch));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(
                        List.of("ha noi major attractions"),
                        List.of("attractions"),
                        null,
                        null,
                        "ha noi")
        );

        assertEquals(2, result.size());
        assertEquals("p2", result.get(0).getId());
        assertEquals("p1", result.get(1).getId());
    }

    @Test
    void ground_PrioritizesHigherPopularityWhenLocationMatchesTie() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );

        Place lowerPopularity = place("p1", "Popular Check-in Spot");
        lowerPopularity.setAddress("Old Quarter, Ha Noi");
        lowerPopularity.setRating(4.9);
        lowerPopularity.setReviewCount(5);

        Place higherPopularity = place("p2", "Temple of Literature");
        higherPopularity.setAddress("Dong Da, Ha Noi");
        higherPopularity.setRating(4.6);
        higherPopularity.setReviewCount(2600);

        when(mongoPlaceSearchProvider.search("ha noi attractions"))
                .thenReturn(List.of(lowerPopularity, higherPopularity));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(
                        List.of("ha noi attractions"),
                        List.of(),
                        null,
                        null,
                        "ha noi")
        );

        assertEquals(2, result.size());
        assertEquals("p2", result.get(0).getId());
        assertEquals("p1", result.get(1).getId());
    }

    @Test
    void ground_PrioritizesDistrictAndCityAliasesOverOffFocusPopularity() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );

        Place districtOneMatch = place("p1", "District 1 Food Walk");
        districtOneMatch.setAddress("Bến Nghé, Quận 1, Sài Gòn");
        districtOneMatch.setRating(4.2);
        districtOneMatch.setReviewCount(180);

        Place cityOnlyMatch = place("p2", "City Eats");
        cityOnlyMatch.setAddress("District 3, Ho Chi Minh City");
        cityOnlyMatch.setRating(4.8);
        cityOnlyMatch.setReviewCount(3200);

        Place offFocusMatch = place("p3", "Famous Food Street");
        offFocusMatch.setAddress("Ba Dinh, Ha Noi");
        offFocusMatch.setRating(5.0);
        offFocusMatch.setReviewCount(5200);

        when(mongoPlaceSearchProvider.search("food tour district 1 ho chi minh city"))
                .thenReturn(List.of(offFocusMatch, cityOnlyMatch, districtOneMatch));

        List<Place> result = service.ground(
                new PlaceSearchExtraction(
                        List.of("food tour district 1 ho chi minh city"),
                        List.of("food"),
                        null,
                        null,
                        "District 1, Ho Chi Minh City")
        );

        assertEquals(3, result.size());
        assertEquals("p1", result.get(0).getId());
        assertEquals("p2", result.get(1).getId());
        assertEquals("p3", result.get(2).getId());
    }

    @Test
    void matchesLocationFocus_RequiresCityWhenDistrictAndCityAreSpecified() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );

        Place sameDistrictDifferentCity = place("p9", "District 1 Food Court");
        sameDistrictDifferentCity.setAddress("District 1, Hai Phong");

        boolean matches = service.matchesLocationFocus(
                new PlaceSearchExtraction(
                        List.of("food tour district 1 ho chi minh city"),
                        List.of("food"),
                        null,
                        null,
                        "District 1, Ho Chi Minh City"),
                sameDistrictDifferentCity
        );

        assertFalse(matches);
    }

    @Test
    void hasLocationFocus_DoesNotTreatExperienceOnlyQueryAsLocationScoped() {
        AiPlaceGroundingService service = new AiPlaceGroundingService(
                "mongo",
                mongoPlaceSearchProvider,
                typesensePlaceSearchProvider,
                placeRepository,
                new VibeHintNormalizer()
        );

        boolean hasFocus = service.hasLocationFocus(
                new PlaceSearchExtraction(
                        List.of("rooftop cafe at sunset"),
                        List.of("rooftop"),
                        "cafe",
                        "romantic",
                        null)
        );

        assertFalse(hasFocus);
    }

    private Place place(String id, String name) {
        Place place = new Place();
        place.setId(id);
        place.setName(name);
        return place;
    }
}
