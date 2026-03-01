package com.bif.app.feature.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.bif.app.domain.model.Favorite;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Objects;

public class FavoritesViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();
    private FavoritesViewModel viewModel;

    @Before
    public void setUp(){
        viewModel = new FavoritesViewModel();
    }

    @Test
    public void init_whenCreated_loadsMockData(){
        // Act
        List<Favorite> result = viewModel.favorites.getValue();

        //Assert
        assertNotNull("Favorite cannot to be null", result);
        assertEquals("Favorite size is not correct", 5, result.size());
    }

    @Test
    public void init_firstItem_isHome() {
        // Act
        List<Favorite> result = viewModel.favorites.getValue();

        // Assert
        assertNotNull(result);
        assertFalse("List cannot be empty", result.isEmpty());
        assertEquals("First item must be Home", "Home", result.get(0).name);
        assertEquals("Address must be 123 Main St", "123 Main St", result.get(0).address);
        assertEquals("Rating must be 5", 5, result.get(0).rating);
    }

    @Test
    public void removeFavorite_existingItem_decreasesListSize() {
        // Arrange
        List<Favorite> originalList = viewModel.favorites.getValue();
        assertNotNull(originalList);
        int originalSize = originalList.size();
        Favorite toRemove = originalList.get(0);

        // Act
        viewModel.removeFavoriteItem(toRemove);

        // Assert
        List<Favorite> updatedList = viewModel.favorites.getValue();
        assertNotNull(updatedList);
        assertEquals("Size must decrease by 1", originalSize - 1, updatedList.size());
    }

    @Test
    public void removeFavorite_existingItem_itemNoLongerInList() {
        // Arrange
        Favorite toRemove = Objects.requireNonNull(viewModel.favorites.getValue()).get(0);

        // Act
        viewModel.removeFavoriteItem(toRemove);

        // Assert
        List<Favorite> updatedList = viewModel.favorites.getValue();
        assertNotNull(updatedList);
        for (Favorite fav : updatedList) {
            assertNotEquals("Home is no longer in the list", "Home", fav.name);
        }
    }

    @Test
    public void removeFavorite_removeAll_listBecomesEmpty() {
        // Act — xóa từng item một
        while (!Objects.requireNonNull(viewModel.favorites.getValue()).isEmpty()) {
            viewModel.removeFavoriteItem(viewModel.favorites.getValue().get(0));
        }

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertTrue("The list must be empty after deleting everything.", result.isEmpty());
    }

    @Test
    public void filterFavorites_byName_returnsMatchingItems() {
        // Act
        viewModel.filterFavorites("Home");

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals("Only 1 item named Home should be returned", 1, result.size());
        assertEquals("Home", result.get(0).name);
    }

    @Test
    public void filterFavorites_byAddress_returnsMatchingItems() {
        // Act
        viewModel.filterFavorites("789");

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Fit Way", result.get(0).name);
    }

    @Test
    public void filterFavorites_noMatch_returnsEmptyList() {
        // Act
        viewModel.filterFavorites("xyz_not_exist");

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertTrue("No match found → list should be empty", result.isEmpty());
    }

    @Test
    public void filterFavorites_emptyQuery_returnsAllItems() {
        // Arrange
        viewModel.filterFavorites("Home");
        assertEquals(1, Objects.requireNonNull(viewModel.favorites.getValue()).size());

        // Act
        viewModel.filterFavorites("");

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals("All 5 items should be returned", 5, result.size());
    }

    @Test
    public void filterFavorites_caseInsensitive_returnsMatch() {
        // Act
        viewModel.filterFavorites("home");

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals("Search should be case-insensitive", 1, result.size());
    }

    @Test
    public void filterFavorites_nullQuery_returnsAllItems() {
        // Act
        viewModel.filterFavorites(null);

        // Assert
        List<Favorite> result = viewModel.favorites.getValue();
        assertNotNull(result);
        assertEquals(5, result.size());
    }
}
