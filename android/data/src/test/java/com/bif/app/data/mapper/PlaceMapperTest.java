package com.bif.app.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.bif.app.core.network.dto.place.PlaceDto;
import com.bif.app.data.source.local.entity.PlaceEntity;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class PlaceMapperTest {

    @Test
    public void toDomain_mapsEntityFieldsCorrectly() {
        PlaceEntity entity = new PlaceEntity();
        entity.id = "p1";
        entity.name = "Test Place";
        entity.address = "123 Main St";
        entity.rating = 4.5;
        entity.latitude = 10.0;
        entity.longitude = 20.0;
        entity.placeSource = "google_maps";

        Place place = PlaceMapper.toDomain(entity);

        assertEquals("p1", place.id);
        assertEquals("Test Place", place.name);
        assertEquals("123 Main St", place.address);
        assertEquals(4.5, place.rating, 0.001);
        assertNotNull(place.location);
        assertEquals(10.0, place.location.latitude, 0.001);
        assertEquals(20.0, place.location.longitude, 0.001);
        assertEquals("google_maps", place.placeSource);
    }

    @Test
    public void toDomainList_mapsMultipleEntities() {
        PlaceEntity e1 = new PlaceEntity();
        e1.id = "p1";
        e1.name = "Place 1";
        PlaceEntity e2 = new PlaceEntity();
        e2.id = "p2";
        e2.name = "Place 2";

        List<Place> places =
                PlaceMapper.toDomainList(Arrays.asList(e1, e2));

        assertEquals(2, places.size());
        assertEquals("p1", places.get(0).id);
        assertEquals("p2", places.get(1).id);
    }

    @Test
    public void toEntity_mapsDomainFieldsCorrectly() {
        Place place = new Place("p1", "Test", "Addr", 3.0,
            new Location(15.0, 25.0), "google_maps");

        PlaceEntity entity = PlaceMapper.toEntity(place);

        assertEquals("p1", entity.id);
        assertEquals("Test", entity.name);
        assertEquals("Addr", entity.address);
        assertEquals(3.0, entity.rating, 0.001);
        assertEquals(15.0, entity.latitude, 0.001);
        assertEquals(25.0, entity.longitude, 0.001);
        assertEquals("google_maps", entity.placeSource);
        // lastSyncedAt should be populated
        assertNotNull(entity.lastSyncedAt);
    }

    @Test
    public void toEntity_nullLocation_setsZeroCoords() {
        Place place = new Place("p1", "Test", "Addr", 3.0, null);

        PlaceEntity entity = PlaceMapper.toEntity(place);

        assertEquals(0.0, entity.latitude, 0.001);
        assertEquals(0.0, entity.longitude, 0.001);
    }

    @Test
    public void fromDto_mapsNetworkDtoToEntity() {
        PlaceDto dto = new PlaceDto();
        dto.id = "d1";
        dto.name = "DTO Place";
        dto.address = "789 Network Blvd";
        dto.rating = 4.0;
        dto.latitude = 35.0;
        dto.longitude = 45.0;
        dto.placeSource = "google_maps";
        dto.persistedByAction = "search_discovered";
        dto.serverVersion = 42;
        dto.tags = Arrays.asList("restaurant", "cafe");

        PlaceEntity entity = PlaceMapper.fromDto(dto);

        assertEquals("d1", entity.id);
        assertEquals("DTO Place", entity.name);
        assertEquals(4.0, entity.rating, 0.001);
        assertEquals("google_maps", entity.placeSource);
        assertEquals("search_discovered", entity.persistedByAction);
        assertEquals(42, entity.serverVersion);
        assertEquals("restaurant,cafe", entity.tags);
    }

    @Test
    public void fromDto_nullTags_skipsTagField() {
        PlaceDto dto = new PlaceDto();
        dto.id = "d1";
        dto.tags = null;

        PlaceEntity entity = PlaceMapper.fromDto(dto);

        assertNull(entity.tags);
    }

    @Test
    public void toDto_mapsDomainToNetworkDto() {
        Place place = new Place("p1", "Place", "Addr", 4.5,
                new Location(10.0, 20.0));

        PlaceDto dto = PlaceMapper.toDto(place);

        assertEquals("p1", dto.id);
        assertEquals("Place", dto.name);
        assertEquals("Addr", dto.address);
        assertEquals(4.5, dto.rating, 0.001);
        assertEquals(10.0, dto.latitude, 0.001);
        assertEquals(20.0, dto.longitude, 0.001);
    }

    @Test
    public void toDto_nullLocation_setsZeroCoords() {
        Place place = new Place("p1", "Place", "Addr", 4.5, null);

        PlaceDto dto = PlaceMapper.toDto(place);

        assertEquals(0.0, dto.latitude, 0.001);
        assertEquals(0.0, dto.longitude, 0.001);
    }

    @Test
    public void fromDtoToDomain_mapsCorrectly() {
        PlaceDto dto = new PlaceDto();
        dto.id = "d1";
        dto.name = "DTO Place";
        dto.address = "Addr";
        dto.rating = 3.5;
        dto.latitude = 10.0;
        dto.longitude = 20.0;
        dto.placeSource = "google_maps";

        Place place = PlaceMapper.fromDto(dto, true);

        assertEquals("d1", place.id);
        assertEquals("DTO Place", place.name);
        assertEquals(3.5, place.rating, 0.001);
        assertEquals(10.0, place.location.latitude, 0.001);
        assertEquals("google_maps", place.placeSource);
    }

    @Test
    public void fromDtoList_mapsMultipleDtos() {
        PlaceDto d1 = new PlaceDto();
        d1.id = "d1";
        d1.name = "First";
        PlaceDto d2 = new PlaceDto();
        d2.id = "d2";
        d2.name = "Second";

        List<Place> places =
                PlaceMapper.fromDtoList(Arrays.asList(d1, d2));

        assertEquals(2, places.size());
        assertEquals("d1", places.get(0).id);
        assertEquals("d2", places.get(1).id);
    }
}

