package com.bif.server.features.place.services;

import com.bif.server.features.place.models.Place;
import com.bif.server.features.place.repositories.PlaceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import com.mongodb.MongoException;
import com.mongodb.bulk.BulkWriteResult;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.data.mongodb.core.BulkOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceBootstrapServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void run_BootstrapsPlacesUsingMongoBulkUpserts() throws Exception {
        PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
        MongoTemplate mongoTemplate = Mockito.mock(MongoTemplate.class);
        BulkOperations bulkOperations = Mockito.mock(BulkOperations.class);
        PlaceBootstrapService service = new PlaceBootstrapService(
                placeRepository,
                mongoTemplate,
                objectMapper);

        Path placesFile = tempDir.resolve("places.geojson");
        java.nio.file.Files.writeString(placesFile, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type":"Feature",
                      "geometry":{"type":"Point","coordinates":[106.7009,10.7769]},
                      "properties":{
                        "id":"place-vn-001",
                        "names":{"primary":"Museum One"},
                        "categories":{"primary":"museum","alternate":["tourism"]},
                        "addresses":[{"freeform":"District 1","country":"VN"}]
                      }
                    },
                    {
                      "type":"Feature",
                      "geometry":{"type":"Point","coordinates":[105.8342,21.0278]},
                      "properties":{
                        "id":"place-vn-002",
                        "names":{"primary":"Cafe Two"},
                        "categories":{"primary":"cafe","alternate":["food"]},
                        "addresses":[{"freeform":"Ha Noi","country":"VN"}]
                      }
                    }
                  ]
                }
                """);

        when(placeRepository.count()).thenReturn(0L);
        when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Place.class)))
                .thenReturn(bulkOperations);
        when(bulkOperations.replaceOne(any(), any(), any())).thenReturn(bulkOperations);
        when(bulkOperations.execute()).thenReturn(BulkWriteResult.acknowledged(0, 0, 0, 0, List.of(), List.of()));
        setField(service, "placesFilePath", placesFile.toString());
        setField(service, "rejectAuditEnabled", false);
        setField(service, "batchSize", 2);

        service.run(new DefaultApplicationArguments());

        verify(bulkOperations, Mockito.times(2)).replaceOne(any(), any(), any());
        verify(bulkOperations).execute();
        verify(placeRepository, never()).saveAll(any());
    }

    @Test
    void run_RetriesTransientBulkWriteFailure() throws Exception {
        PlaceRepository placeRepository = Mockito.mock(PlaceRepository.class);
        MongoTemplate mongoTemplate = Mockito.mock(MongoTemplate.class);
        BulkOperations bulkOperations = Mockito.mock(BulkOperations.class);
        PlaceBootstrapService service = new PlaceBootstrapService(
                placeRepository,
                mongoTemplate,
                objectMapper);

        Path placesFile = tempDir.resolve("places-retry.geojson");
        java.nio.file.Files.writeString(placesFile, """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type":"Feature",
                      "geometry":{"type":"Point","coordinates":[106.7009,10.7769]},
                      "properties":{
                        "id":"place-vn-retry",
                        "names":{"primary":"Retry Museum"},
                        "categories":{"primary":"museum","alternate":["tourism"]},
                        "addresses":[{"freeform":"District 1","country":"VN"}]
                      }
                    }
                  ]
                }
                """);

        when(placeRepository.count()).thenReturn(0L);
        when(mongoTemplate.bulkOps(eq(BulkOperations.BulkMode.UNORDERED), eq(Place.class)))
                .thenReturn(bulkOperations);
        when(bulkOperations.replaceOne(any(), any(), any())).thenReturn(bulkOperations);
        when(bulkOperations.execute())
                .thenThrow(new MongoException("transient"))
                .thenReturn(BulkWriteResult.acknowledged(0, 0, 0, 0, List.of(), List.of()));
        setField(service, "placesFilePath", placesFile.toString());
        setField(service, "rejectAuditEnabled", false);
        setField(service, "batchSize", 1);
        setField(service, "batchWriteMaxAttempts", 2);
        setField(service, "batchWriteRetryBackoffMs", 0L);

        service.run(new DefaultApplicationArguments());

        verify(bulkOperations, Mockito.times(2)).execute();
        verify(placeRepository, never()).saveAll(any());
    }


    @Test
    void parseOvertureFeature_ExtractsStructuredMetadataAndNormalizedFields() throws Exception {
        PlaceBootstrapService service = new PlaceBootstrapService(
                Mockito.mock(PlaceRepository.class),
                Mockito.mock(MongoTemplate.class),
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
                Mockito.mock(MongoTemplate.class),
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
                Mockito.mock(MongoTemplate.class),
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
                Mockito.mock(MongoTemplate.class),
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

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private Place invokeParse(PlaceBootstrapService service, JsonNode featureNode) throws Exception {
        Method parseMethod = PlaceBootstrapService.class
                .getDeclaredMethod("parseOvertureFeature", JsonNode.class);
        parseMethod.setAccessible(true);
        return (Place) parseMethod.invoke(service, featureNode);
    }
}
