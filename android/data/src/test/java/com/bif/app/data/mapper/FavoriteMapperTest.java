package com.bif.app.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.bif.app.data.source.local.entity.FavoriteEntity;
import com.bif.app.domain.model.Favorite;

import org.junit.Test;

public class FavoriteMapperTest {

    @Test
    public void toDomain_ValidEntity_ReturnsMappedDomain() {
        // Arrange
        FavoriteEntity entity = new FavoriteEntity();
        entity.id = 1;
        entity.name = "Home";
        entity.address = "123 Main St";

        // Act
        Favorite domain = FavoriteMapper.toDomain(entity);

        // Assert
        assertNotNull(domain);
        assertEquals(1, domain.id);
        assertEquals("Home", domain.name);
        assertEquals("123 Main St", domain.address);
    }

    @Test
    public void toEntity_ValidDomain_ReturnsMappedEntity() {
        // Arrange
        Favorite domain = new Favorite();
        domain.id = 2;
        domain.name = "Work";
        domain.rating = 5;

        // Act
        FavoriteEntity entity = FavoriteMapper.toEntity(domain);

        // Assert
        assertNotNull(entity);
        assertEquals(2, entity.id);
        assertEquals("Work", entity.name);
        assertEquals(5, entity.rating);
    }

    @Test
    public void toDomain_NullEntity_ReturnsNull() {
        assertNull(FavoriteMapper.toDomain(null));
    }

    @Test
    public void toEntity_NullDomain_ReturnsNull() {
        assertNull(FavoriteMapper.toEntity(null));
    }
}