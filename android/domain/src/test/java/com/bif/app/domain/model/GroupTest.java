package com.bif.app.domain.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class GroupTest {

    @Test
    public void group_initialization_setsValuesCorrectly() {
        List<Friend> members = Arrays.asList(
                new Friend(1, "An", "A", 111, true),
                new Friend(2, "Bình", "B", 222, false)
        );
        Group group = new Group(10, "Developers", "D", 654321, members, true);

        assertEquals(10, group.getId());
        assertEquals("Developers", group.getName());
        assertEquals("D", group.getAvatarLetter());
        assertEquals(654321, group.getAvatarColor());
        assertEquals(2, group.getMembers().size());
        assertTrue(group.isOwner());
    }

    @Test
    public void getMemberCount_withMembers_returnsCorrectCount() {
        List<Friend> members = Arrays.asList(
                new Friend(1, "An", "A", 111, true),
                new Friend(2, "Bình", "B", 222, false),
                new Friend(3, "Cường", "C", 333, true)
        );
        Group group = new Group(1, "Team", "T", 0xFF00FF, members, false);

        assertEquals(3, group.getMemberCount());
    }

    @Test
    public void getMemberCount_withNullMembers_returnsZero() {
        Group group = new Group(1, "Empty", "E", 0xFF00FF, null, true);

        assertEquals(0, group.getMemberCount());
    }

    @Test
    public void getMemberCount_withEmptyMembers_returnsZero() {
        Group group = new Group(1, "Empty", "E", 0xFF00FF, new ArrayList<>(), true);

        assertEquals(0, group.getMemberCount());
    }

    @Test
    public void isOwner_true_returnsTrue() {
        Group group = new Group(1, "My Group", "M", 0, new ArrayList<>(), true);

        assertTrue(group.isOwner());
    }

    @Test
    public void isOwner_false_returnsFalse() {
        Group group = new Group(1, "Other Group", "O", 0, new ArrayList<>(), false);

        assertFalse(group.isOwner());
    }
}
