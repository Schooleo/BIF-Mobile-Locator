package com.bif.app.domain.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FriendTest {

    @Test
    public void friend_initialization_setsValuesCorrectly() {
        Friend friend = new Friend(1, "John Doe", "J", 123456, true);

        assertEquals(1, friend.getId());
        assertEquals("John Doe", friend.getName());
        assertEquals("J", friend.getAvatarLetter());
        assertEquals(123456, friend.getAvatarColor());
        assertTrue(friend.isOnline());
        assertEquals(0L, friend.getFriendshipCreatedAt());
    }

    @Test
    public void friend_initialization_withFriendshipCreatedAt_setsTimestamp() {
        Friend friend = new Friend(1, "server-1", "John Doe", "J", 123456, true, 1234L);

        assertEquals("server-1", friend.getServerUserId());
        assertEquals(1234L, friend.getFriendshipCreatedAt());
    }
}
