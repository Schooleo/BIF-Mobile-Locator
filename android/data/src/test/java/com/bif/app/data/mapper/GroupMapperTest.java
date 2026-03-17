package com.bif.app.data.mapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.bif.app.data.source.local.entity.FriendEntity;
import com.bif.app.data.source.local.entity.GroupEntity;
import com.bif.app.data.source.local.entity.GroupWithFriends;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupMapperTest {

    private GroupMapper mapper;

    @Before
    public void setup() {
        mapper = new GroupMapper();
    }

    @Test
    public void mapToDomain_ValidGroupWithFriends_ReturnsMappedGroup() {
        // Arrange
        GroupWithFriends gwf = new GroupWithFriends();
        gwf.group = new GroupEntity(5, "DevTeam", "D", 0xFF03DAC5, true);

        FriendEntity f1 = new FriendEntity();
        f1.id = 1; f1.name = "An"; f1.avatarLetter = "A"; f1.avatarColor = 111; f1.isOnline = true;
        FriendEntity f2 = new FriendEntity();
        f2.id = 2; f2.name = "Bình"; f2.avatarLetter = "B"; f2.avatarColor = 222; f2.isOnline = false;
        gwf.friends = Arrays.asList(f1, f2);

        // Act
        Group result = mapper.mapToDomain(gwf);

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getId());
        assertEquals("DevTeam", result.getName());
        assertEquals("D", result.getAvatarLetter());
        assertEquals(0xFF03DAC5, result.getAvatarColor());
        assertTrue(result.isOwner());
        assertEquals(2, result.getMembers().size());
        assertEquals("An", result.getMembers().get(0).getName());
        assertEquals("Bình", result.getMembers().get(1).getName());
    }

    @Test
    public void mapToDomain_NullInput_ReturnsNull() {
        assertNull(mapper.mapToDomain(null));
    }

    @Test
    public void mapToDomain_NullGroupField_ReturnsNull() {
        GroupWithFriends gwf = new GroupWithFriends();
        gwf.group = null;
        gwf.friends = new ArrayList<>();

        assertNull(mapper.mapToDomain(gwf));
    }

    @Test
    public void mapToDomain_NullFriendsList_ReturnsDomainWithEmptyMembers() {
        GroupWithFriends gwf = new GroupWithFriends();
        gwf.group = new GroupEntity(1, "Solo", "S", 0, false);
        gwf.friends = null;

        Group result = mapper.mapToDomain(gwf);

        assertNotNull(result);
        assertNotNull(result.getMembers());
        assertEquals(0, result.getMembers().size());
    }

    @Test
    public void mapToEntity_ValidGroup_ReturnsMappedEntity() {
        // Arrange
        List<Friend> members = List.of(
                new Friend(1, "An", "A", 111, true)
        );
        Group domain = new Group(7, "Designers", "D", 0xFFBB86FC, members, true);

        // Act
        GroupEntity entity = mapper.mapToEntity(domain);

        // Assert
        assertNotNull(entity);
        assertEquals(7, entity.getId());
        assertEquals("Designers", entity.getName());
        assertEquals("D", entity.getAvatarLetter());
        assertEquals(0xFFBB86FC, entity.getAvatarColor());
        assertTrue(entity.isOwner());
    }

    @Test
    public void mapToEntity_NullInput_ReturnsNull() {
        assertNull(mapper.mapToEntity(null));
    }

    @Test
    public void mapToDomainList_ValidList_ReturnsMappedList() {
        // Arrange
        GroupWithFriends gwf1 = new GroupWithFriends();
        gwf1.group = new GroupEntity(1, "Alpha", "A", 0, true);
        gwf1.friends = new ArrayList<>();

        GroupWithFriends gwf2 = new GroupWithFriends();
        gwf2.group = new GroupEntity(2, "Beta", "B", 0, false);
        gwf2.friends = new ArrayList<>();

        List<GroupWithFriends> entities = Arrays.asList(gwf1, gwf2);

        // Act
        List<Group> result = mapper.mapToDomainList(entities);

        // Assert
        assertEquals(2, result.size());
        assertEquals("Alpha", result.get(0).getName());
        assertEquals("Beta", result.get(1).getName());
    }

    @Test
    public void mapToDomainList_EmptyList_ReturnsEmptyList() {
        List<Group> result = mapper.mapToDomainList(new ArrayList<>());

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    public void mapToDomainList_NullList_ReturnsEmptyList() {
        List<Group> result = mapper.mapToDomainList(null);

        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
