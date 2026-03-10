package com.bif.app.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class GroupTest {

    @Test
    public void group_initialization_setsValuesCorrectly() {
        Group group = new Group("Developers", "D", 654321, 10);

        assertEquals("Developers", group.getName());
        assertEquals("D", group.getAvatarLetter());
        assertEquals(654321, group.getAvatarColor());
        assertEquals(10, group.getMemberCount());
    }
}
