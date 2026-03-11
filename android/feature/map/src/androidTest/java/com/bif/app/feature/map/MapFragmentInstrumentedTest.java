package com.bif.app.feature.map;

import android.content.Context;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test for MapFragment.
 */
@RunWith(AndroidJUnit4.class)
public class MapFragmentInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void fragment_canBeInstantiated() {
        // Act
        MapFragment fragment = new MapFragment();

        // Assert
        assertNotNull("Fragment should be instantiated", fragment);
    }

    @Test
    public void fragment_withLocationArgument_hasArguments() {
        // Arrange
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putString("location", "HCMUS");

        // Act
        fragment.setArguments(args);

        // Assert
        assertNotNull("Fragment should have arguments", fragment.getArguments());
        assertEquals("Location argument should be set", "HCMUS",
                fragment.getArguments().getString("location"));
    }

    // ─── argument handling ────────────────────────────────────────────────────

    @Test
    public void fragment_withFavoriteIdArgument_hasArguments() {
        // Arrange
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putInt("favId", 42);
        args.putString("favName", "Café Central");

        // Act
        fragment.setArguments(args);

        // Assert
        assertNotNull(fragment.getArguments());
        assertEquals(42, fragment.getArguments().getInt("favId"));
        assertEquals("Café Central", fragment.getArguments().getString("favName"));
    }

    @Test
    public void fragment_withMultipleArguments_allArgumentsPreserved() {
        // Arrange
        MapFragment fragment = new MapFragment();
        Bundle args = new Bundle();
        args.putDouble("latitude", 10.762);
        args.putDouble("longitude", 106.682);
        args.putFloat("zoom", 15.5f);
        args.putString("label", "HCMUS");

        // Act
        fragment.setArguments(args);
        Bundle storedArgs = fragment.getArguments();

        // Assert
        assertNotNull(storedArgs);
        assertEquals(10.762, storedArgs.getDouble("latitude"), 0.001);
        assertEquals(106.682, storedArgs.getDouble("longitude"), 0.001);
        assertEquals(15.5f, storedArgs.getFloat("zoom"), 0.01f);
        assertEquals("HCMUS", storedArgs.getString("label"));
    }

    @Test
    public void fragment_withNoArguments_getArgumentsReturnsNull() {
        // Arrange
        MapFragment fragment = new MapFragment();

        // Assert: no arguments set → getArguments() is null by default
        assertNull("Fragment with no arguments should return null", fragment.getArguments());
    }

    @Test
    public void fragment_newInstance_isIndependentFromAnotherInstance() {
        // Arrange
        MapFragment fragment1 = new MapFragment();
        MapFragment fragment2 = new MapFragment();

        Bundle args1 = new Bundle();
        args1.putString("location", "HCMUS");
        fragment1.setArguments(args1);

        // Act: fragment2 has no args
        // Assert: the two instances are independent
        assertNotSame(fragment1, fragment2);
        assertNull(fragment2.getArguments());
        assertEquals("HCMUS", fragment1.getArguments().getString("location"));
    }
}