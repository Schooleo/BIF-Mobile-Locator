package com.bif.app.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.domain.model.Friend;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class FriendMapperTest {
    @Test
    public void toDomain_ValidEntity_ReturnsMappedDomain() {
        FriendEntity entity = new FriendEntity();
        entity.name = "An";
        entity.avatarLetter = "A";
        entity.avatarColor = 12345;
        entity.isOnline = true;

        Friend domain = FriendMapper.toDomain(entity);

        assertNotNull(domain);
        assertEquals("An", domain.getName());
        assertEquals("A", domain.getAvatarLetter());
        assertEquals(12345, domain.getAvatarColor());
        assertTrue(domain.isOnline());
    }

    @Test
    public void toEntity_ValidDomain_ReturnsMappedEntity() {
        Friend domain = new Friend("Bình", "B", 54321, false);

        FriendEntity entity = FriendMapper.toEntity(domain);

        assertNotNull(entity);
        assertEquals("Bình", entity.name);
        assertEquals("B", entity.avatarLetter);
        assertEquals(54321, entity.avatarColor);
        assertFalse(entity.isOnline);
    }

    @Test
    public void toDomainList_ValidEntityList_ReturnsDomainList() {
        List<FriendEntity> entityList = new ArrayList<>();
        FriendEntity e1 = new FriendEntity(); e1.name = "A";
        FriendEntity e2 = new FriendEntity(); e2.name = "B";
        entityList.add(e1);
        entityList.add(e2);

        List<Friend> domainList = FriendMapper.toDomainList(entityList);

        assertEquals(2, domainList.size());
        assertEquals("A", domainList.get(0).getName());
        assertEquals("B", domainList.get(1).getName());
    }

    @Test
    public void toDomainList_EmptyList_ReturnsEmptyDomainList() {
        List<Friend> list = FriendMapper.toDomainList(new ArrayList<>());
        assertNotNull(list);
        assertEquals(0, list.size());
    }

    @Test
    public void toDomainList_NullList_ReturnsEmptyDomainList() {
        List<Friend> list = FriendMapper.toDomainList(null);
        assertNotNull(list);
        assertEquals(0, list.size());
    }
}
