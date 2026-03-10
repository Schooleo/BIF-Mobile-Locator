package com.bif.app.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class FriendTest {

    @Test
    public void friend_initialization_setsValuesCorrectly() {
        Friend friend = new Friend("John Doe", "J", 123456, true);

        assertEquals("John Doe", friend.getName());
        assertEquals("J", friend.getAvatarLetter());
        assertEquals(123456, friend.getAvatarColor());
        assertTrue(friend.isOnline());
    }
}
