package com.bif.app.core.utils;

import org.junit.Test;

import static org.junit.Assert.*;

public class DistanceUtilsTest {

    private static final double DELTA = 0.01; // Tolerance for floating point comparison

    @Test
    public void calculateDistance_samePoint_returnsZero() {
        double lat = 10.0;
        double lon = 20.0;
        double result = DistanceUtils.calculateDistance(lat, lon, lat, lon);
        assertEquals("Distance to self should be 0", 0.0, result, DELTA);
    }

    @Test
    public void calculateDistance_knownPoints_returnsCorrectDistance() {
        // Approximate distance between New York (40.7128, -74.0060) and London (51.5074, -0.1278)
        // is roughly 5570 km.
        double result = DistanceUtils.calculateDistance(40.7128, -74.0060, 51.5074, -0.1278);
        
        // Allow for some variance due to precision, but check it's in the ballpark
        assertEquals("Distance should be approx 5570km", 5570.0, result, 5.0);
    }

    @Test
    public void calculateDistance_poles_returnsCorrectDistance() {
        // Distance from North Pole (90, 0) to South Pole (-90, 0) is approx 20015 km (half circumference)
        double result = DistanceUtils.calculateDistance(90.0, 0.0, -90.0, 0.0);
        assertEquals("Distance between poles", 20015.0, result, 10.0);
    }
}
