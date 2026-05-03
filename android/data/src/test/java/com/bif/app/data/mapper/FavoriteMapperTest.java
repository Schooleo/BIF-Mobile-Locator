package com.bif.app.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.bif.app.core.network.dto.favorite.FavoriteDto;
import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.domain.model.Favorite;

import org.junit.Test;

public class FavoriteMapperTest {

    @Test
    public void toDomain_ValidEntity_ReturnsMappedDomain() {
        // Arrange
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = "fav-1";
        entity.placeId = "place-1";
        entity.name = "Home";
        entity.address = "123 Main St";
        entity.serverVersion = 10L;
        entity.deleted = true;
        entity.userId = "u1";

        // Act
        Favorite domain = FavoriteMapper.toDomain(entity);

        // Assert
        assertNotNull(domain);
        assertEquals("fav-1", domain.id);
        assertEquals("place-1", domain.placeId);
        assertEquals("Home", domain.name);
        assertEquals("123 Main St", domain.address);
        assertEquals(10L, domain.serverVersion);
        assertEquals(true, domain.deleted);
        assertEquals("u1", domain.userId);
    }

    @Test
    public void toEntity_ValidDomain_ReturnsMappedEntity() {
        // Arrange
        Favorite domain = new Favorite();
        domain.id = "fav-2";
        domain.placeId = "place-2";
        domain.name = "Work";
        domain.rating = 5;
        domain.serverVersion = 20L;
        domain.deleted = false;
        domain.userId = "u2";

        // Act
        FavoriteEntity entity = FavoriteMapper.toEntity(domain);

        // Assert
        assertNotNull(entity);
        assertEquals("fav-2", entity.id);
        assertEquals("place-2", entity.placeId);
        assertEquals("Work", entity.name);
        assertEquals(5, entity.rating);
        assertEquals(20L, entity.serverVersion);
        assertEquals(false, entity.deleted);
        assertEquals("u2", entity.userId);
    }

    @Test
    public void toDomain_NullEntity_ReturnsNull() {
        assertNull(FavoriteMapper.toDomain((FavoriteEntity)null));
    }

    @Test
    public void toEntity_NullDomain_ReturnsNull() {
        assertNull(FavoriteMapper.toEntity(null));
    }

    @Test
    public void fromDto_ValidDto_ReturnsMappedEntity() {
        // Arrange
        FavoriteDto dto = new FavoriteDto();
        dto.id = "dto-1";
        dto.placeId = "place-3";
        dto.name = "Sync Place";
        dto.serverVersion = 42;
        dto.deleted = true;
        dto.userId = "user-abc";

        // Act
        FavoriteEntity entity = FavoriteMapper.fromDto(dto);

        // Assert
        assertNotNull(entity);
        assertEquals("dto-1", entity.id);
        assertEquals("place-3", entity.placeId);
        assertEquals("Sync Place", entity.name);
        assertEquals(42, entity.serverVersion);
        assertTrue(entity.deleted);
        assertEquals("user-abc", entity.userId);
    }

    @Test
    public void toDto_ValidDomain_ReturnsMappedDto() {
        // Arrange
        Favorite domain = new Favorite();
        domain.id = "dom-1";
        domain.placeId = "place-4";
        domain.name = "Domain Place";
        domain.rating = 4;

        // Act
        FavoriteDto dto = FavoriteMapper.toDto(domain, "user-xyz");

        // Assert
        assertNotNull(dto);
        assertEquals("dom-1", dto.id);
        assertNull(dto.placeId);
        assertEquals("Domain Place", dto.name);
        assertEquals(4, dto.rating);
        assertEquals("user-xyz", dto.userId);
        
        // Test with null userId argument, should fallback to domain.userId
        domain.userId = "dom-user";
        FavoriteDto dto2 = FavoriteMapper.toDto(domain, null);
        assertEquals("dom-user", dto2.userId);
    }

    private void assertTrue(boolean condition) {
        org.junit.Assert.assertTrue(condition);
    }
}
