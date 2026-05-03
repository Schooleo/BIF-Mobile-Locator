package com.bif.server.features.search.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.search.config.TypesenseProperties;
import com.bif.server.features.search.dto.PlaceSearchRequestDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TypesensePlaceSearchProviderTest {

    @Test
    void search_MapsStructuredGroundingFieldsFromTypesenseDocument() throws Exception {
        TypesenseProperties props = new TypesenseProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setHost("localhost");
        props.setPort(8108);
        props.setPlacesCollection("places");

        HttpClient mockClient = Mockito.mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {
                  "hits":[
                    {
                      "document":{
                        "id":"p-1",
                        "name":"Ben Thanh Market",
                        "nameNormalized":"ben thanh market",
                        "address":"District 1, Ho Chi Minh City",
                        "addressNormalized":"district 1 ho chi minh city",
                        "country":"VN",
                        "region":"Ho Chi Minh",
                        "locality":"Ho Chi Minh City",
                        "city":"ho chi minh city",
                        "district":"district 1",
                        "rating":4.7,
                        "reviewCount":1250,
                        "tags":["market","shopping"],
                        "categoryMain":"market",
                        "categoryAlternates":["shopping","tourism"],
                        "location":[10.7721,106.6982]
                      }
                    }
                  ]
                }
                """);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        TypesensePlaceSearchProvider provider = new TypesensePlaceSearchProvider(
                props,
                new ObjectMapper(),
                mockClient);

        List<Place> places = provider.search(
                new PlaceSearchRequestDTO("market", 10.7769, 106.7009, 8),
                "name,address,tags");

        assertEquals(1, places.size());
        Place place = places.getFirst();
        assertEquals("p-1", place.getId());
        assertEquals("Ben Thanh Market", place.getName());
        assertEquals("VN", place.getCountry());
        assertEquals("Ho Chi Minh", place.getRegion());
        assertEquals("Ho Chi Minh City", place.getLocality());
        assertEquals("ho chi minh city", place.getCity());
        assertEquals("district 1", place.getDistrict());
        assertEquals("market", place.getCategoryMain());
        assertEquals(List.of("shopping", "tourism"), place.getCategoryAlternates());
        assertEquals("ben thanh market", place.getNameNormalized());
        assertEquals("district 1 ho chi minh city", place.getAddressNormalized());
        assertTrue(place.getTags().contains("shopping"));
        assertEquals(10.7721, place.getLocation().getLatitude());
        assertEquals(106.6982, place.getLocation().getLongitude());
    }
}

