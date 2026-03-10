package com.bif.app.feature.favorites;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.bif.app.domain.model.Favorite;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Instrumented tests for {@link FavoritesFragment} and {@link FavoriteAdapter}.
 * Fragment lifecycle tests verify argument handling without requiring Hilt or Navigation,
 * since those concerns belong to integration/E2E testing.
 * Adapter tests exercise add/remove and query-result display logic on-device.
 */
@RunWith(AndroidJUnit4.class)
public class FavoritesFragmentInstrumentedTest {

    // ─── FavoritesFragment — instantiation & argument handling ────────────────

    @Test
    public void fragment_canBeInstantiated() {
        // Act
        FavoritesFragment fragment = new FavoritesFragment();

        // Assert
        assertNotNull("FavoritesFragment should be instantiatable", fragment);
    }

    @Test
    public void fragment_withNoArguments_getArgumentsReturnsNull() {
        // Act
        FavoritesFragment fragment = new FavoritesFragment();

        // Assert
        assertNull("A fragment with no arguments should return null from getArguments()",
                fragment.getArguments());
    }

    @Test
    public void fragment_setArguments_bundleIsPreserved() {
        // Arrange
        FavoritesFragment fragment = new FavoritesFragment();
        Bundle bundle = new Bundle();
        bundle.putString("filter", "cafe");

        // Act
        fragment.setArguments(bundle);

        // Assert
        assertNotNull(fragment.getArguments());
        assertEquals("cafe", fragment.getArguments().getString("filter"));
    }

    @Test
    public void fragment_twoInstances_areIndependent() {
        // Arrange
        FavoritesFragment f1 = new FavoritesFragment();
        FavoritesFragment f2 = new FavoritesFragment();

        Bundle args = new Bundle();
        args.putString("tag", "test");
        f1.setArguments(args);

        // Assert
        assertNotSame(f1, f2);
        assertNull("f2 should not share arguments with f1", f2.getArguments());
    }

    // ─── FavoriteAdapter — submitList & getItemCount ───────────────────────────

    /** No-op listener used to satisfy the adapter constructor. */
    private static final FavoriteAdapter.OnFavoriteClickListener NO_OP_LISTENER =
            new FavoriteAdapter.OnFavoriteClickListener() {
                @Override public void onFavoriteClicked(Favorite favorite) { }
                @Override public void onFavoriteRemoved(Favorite favorite) { }
            };

    @Test
    public void favoriteAdapter_submitNull_itemCountIsZero() {
        // Arrange
        FavoriteAdapter adapter = new FavoriteAdapter(NO_OP_LISTENER);

        // Act
        adapter.submitList(null);

        // Assert
        assertEquals("Null list should result in count 0", 0, adapter.getItemCount());
    }

    @Test
    public void favoriteAdapter_submitEmptyList_itemCountIsZero() {
        // Arrange
        FavoriteAdapter adapter = new FavoriteAdapter(NO_OP_LISTENER);

        // Act
        adapter.submitList(Collections.emptyList());

        // Assert
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void favoriteAdapter_submitList_itemCountMatchesList() {
        // Arrange
        FavoriteAdapter adapter = new FavoriteAdapter(NO_OP_LISTENER);
        List<Favorite> list = Arrays.asList(
                makeFavorite(1, "Home",    "123 Main St"),
                makeFavorite(2, "Work",    "456 Corp Blvd"),
                makeFavorite(3, "Gym",     "789 Fitness Ave")
        );

        // Act
        adapter.submitList(list);

        // Assert
        assertEquals(3, adapter.getItemCount());
    }

    @Test
    public void favoriteAdapter_submitListTwice_itemCountReflectsLatestList() {
        // Arrange
        FavoriteAdapter adapter = new FavoriteAdapter(NO_OP_LISTENER);
        List<Favorite> first  = Collections.singletonList(makeFavorite(1, "Home", "123 Main St"));
        List<Favorite> second = Arrays.asList(
                makeFavorite(2, "Cafe A", "Addr 1"),
                makeFavorite(3, "Cafe B", "Addr 2")
        );

        // Act
        adapter.submitList(first);
        assertEquals("After first submit: count should be 1", 1, adapter.getItemCount());

        adapter.submitList(second);

        // Assert
        assertEquals("After second submit: count should be 2", 2, adapter.getItemCount());
    }

    @Test
    public void favoriteAdapter_afterSubmitThenNull_itemCountDropsToZero() {
        // Arrange
        FavoriteAdapter adapter = new FavoriteAdapter(NO_OP_LISTENER);
        adapter.submitList(Arrays.asList(
                makeFavorite(1, "Place A", "Addr A"),
                makeFavorite(2, "Place B", "Addr B")
        ));
        assertEquals(2, adapter.getItemCount());

        // Act: simulate search that returns no results (null)
        adapter.submitList(null);

        // Assert
        assertEquals(0, adapter.getItemCount());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Favorite makeFavorite(int id, String name, String address) {
        Favorite f = new Favorite();
        f.id = id;
        f.name = name;
        f.address = address;
        return f;
    }
}
