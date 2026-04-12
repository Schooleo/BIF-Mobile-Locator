package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceBootstrapServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseOvertureFeature_ExtractsStructuredMetadataAndNormalizedFields() throws Exception {
        PlaceBootstrapService service = new PlaceBootstrapService(
                Mockito.mock(PlaceRepository.class),
                objectMapper);

        JsonNode featureNode = objectMapper.readTree("""
                {
                  "type":"Feature",
                  "geometry":{"type":"Point","coordinates":[106.7009,10.7769]},
                  "properties":{
                    "id":"place-vn-001",
                    "names":{"primary":"Notre Dame Cathedral"},
                    "basic_category":"landmark",
                    "categories":{"primary":"church","alternate":["historic","tourism"]},
                    "addresses":[
                      {
                        "freeform":"01 Cong xa Paris, Ben Nghe, District 1, Ho Chi Minh City",
                        "locality":"Ho Chi Minh City",
                        "region":"Ho Chi Minh",
                        "country":"VN"
                      }
                    ]
                  }
                }
                """);

        Place place = invokeParse(service, featureNode);

        assertNotNull(place);
        assertEquals("place-vn-001", place.getId());
        assertEquals("Notre Dame Cathedral", place.getName());
        assertEquals("01 Cong xa Paris, Ben Nghe, District 1, Ho Chi Minh City", place.getAddress());
        assertEquals("VN", place.getCountry());
        assertEquals("Ho Chi Minh", place.getRegion());
        assertEquals("Ho Chi Minh City", place.getLocality());
        assertEquals("ho chi minh city", place.getCity());
        assertEquals("district 1", place.getDistrict());
        assertEquals("church", place.getCategoryMain());
        assertEquals(List.of("historic", "tourism"), place.getCategoryAlternates());
        assertTrue(place.getTags().contains("church"));
        assertTrue(place.getTags().contains("historic"));
        assertTrue(place.getTags().contains("landmark"));
        assertEquals("notre dame cathedral", place.getNameNormalized());
        assertTrue(place.getAddressNormalized().contains("district 1"));
    }

    @Test
    void parseOvertureFeature_AddressFallsBackToLocalityRegionCountryWhenFreeformMissing() throws Exception {
        PlaceBootstrapService service = new PlaceBootstrapService(
                Mockito.mock(PlaceRepository.class),
                objectMapper);

        JsonNode featureNode = objectMapper.readTree("""
                {
                  "type":"Feature",
                  "geometry":{"type":"Point","coordinates":[105.8342,21.0278]},
                  "properties":{
                    "id":"place-vn-002",
                    "names":{"primary":"Temple of Literature"},
                    "categories":{"primary":"museum","alternate":[]},
                    "addresses":[
                      {
                        "freeform":"",
                        "locality":"Ha Noi",
                        "region":"Ha Noi",
                        "country":"VN"
                      }
                    ]
                  }
                }
                """);

        Place place = invokeParse(service, featureNode);

        assertNotNull(place);
        assertEquals("Ha Noi, Ha Noi, VN", place.getAddress());
        assertEquals("ha noi", place.getCity());
        assertEquals("museum", place.getCategoryMain());
    }

    @Test
    void parseOvertureFeature_RejectsOutsideVietnamBoundingBox() throws Exception {
        PlaceBootstrapService service = new PlaceBootstrapService(
                Mockito.mock(PlaceRepository.class),
                objectMapper);

        JsonNode featureNode = objectMapper.readTree("""
                {
                  "type":"Feature",
                  "geometry":{"type":"Point","coordinates":[0.0,0.0]},
                  "properties":{
                    "id":"place-outside-001",
                    "names":{"primary":"Valid Place"},
                    "categories":{"primary":"museum","alternate":["tourism"]},
                    "addresses":[{"freeform":"Nowhere","country":"VN"}]
                  }
                }
                """);

        Place place = invokeParse(service, featureNode);
        assertNull(place);
    }

    @Test
    void parseOvertureFeature_RejectsJunkNames() throws Exception {
        PlaceBootstrapService service = new PlaceBootstrapService(
                Mockito.mock(PlaceRepository.class),
                objectMapper);

        JsonNode featureNode = objectMapper.readTree("""
                {
                  "type":"Feature",
                  "geometry":{"type":"Point","coordinates":[106.7,10.77]},
                  "properties":{
                    "id":"place-junk-001",
                    "names":{"primary":"N/A"},
                    "categories":{"primary":"museum","alternate":["tourism"]},
                    "addresses":[{"freeform":"District 1","country":"VN"}]
                  }
                }
                """);

        Place place = invokeParse(service, featureNode);
        assertNull(place);
    }

    private Place invokeParse(PlaceBootstrapService service, JsonNode featureNode) throws Exception {
        Method parseMethod = PlaceBootstrapService.class
                .getDeclaredMethod("parseOvertureFeature", JsonNode.class);
        parseMethod.setAccessible(true);
        return (Place) parseMethod.invoke(service, featureNode);
    }
}
