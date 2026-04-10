package com.bif.app.domain.model;

import org.junit.Test;
import static org.junit.Assert.*;

public class FavoriteTest {

    @Test
    public void favorite_initialization_setsValuesCorrectly() {
        Favorite favorite = new Favorite();
        favorite.id = "fav-1";
        favorite.name = "My Favorite Place";
        favorite.latitude = 10.0;
        favorite.longitude = 20.0;
        favorite.address = "123 Main St";
        favorite.description = "A great place";
        favorite.notes = "Remember to carry cash";
        favorite.rating = 5;

        assertEquals("fav-1", favorite.id);
        assertEquals("My Favorite Place", favorite.name);
        assertEquals(10.0, favorite.latitude, 0.0);
        assertEquals(20.0, favorite.longitude, 0.0);
        assertEquals("123 Main St", favorite.address);
        assertEquals("A great place", favorite.description);
        assertEquals("Remember to carry cash", favorite.notes);
        assertEquals(5, favorite.rating);
    }
}
