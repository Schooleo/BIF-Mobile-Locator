package com.bif.app.feature.map;

import android.content.Context;
import android.os.Bundle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.testing.HiltAndroidTest;

import static org.junit.Assert.*;

/**
 * Instrumented test for MapFragment.
 * Note: This requires HiltAndroidTest annotation for dependency injection.
 */
@HiltAndroidTest
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

    @Test
    public void fragmentArgs_fromBundle_parsesCorrectly() {
        // Arrange
        Bundle args = new Bundle();
        args.putString("location", "Ho Chi Minh City University of Science");

        // Act
        MapFragmentArgs fragmentArgs = MapFragmentArgs.fromBundle(args);

        // Assert
        assertNotNull("FragmentArgs should be created", fragmentArgs);
        Assert.assertEquals("Location should be parsed",
                "Ho Chi Minh City University of Science",
                fragmentArgs.getLocation());
    }
}