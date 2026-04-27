package com.bif.app.feature.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaceDisplayTextResolverTest {

    @Test
    public void resolveTitle_prefersMeaningfulPreferredNameOverAddressLikeReverseGeocode() {
        String resolved = PlaceDisplayTextResolver.resolveTitle(
                "Truong Trung hoc pho thong chuyen",
                "235 Nguyen Van Cu, Thu Duc, Ho Chi Minh City",
                "235 Nguyen Van Cu, Thu Duc, Ho Chi Minh City");

        assertEquals("Truong Trung hoc pho thong chuyen", resolved);
    }

    @Test
    public void resolveTitle_fallsBackToStableLabelWhenOnlyAddressIsAvailable() {
        String resolved = PlaceDisplayTextResolver.resolveTitle(
                null,
                "235 Nguyen Van Cu, Thu Duc, Ho Chi Minh City",
                "235 Nguyen Van Cu, Thu Duc, Ho Chi Minh City");

        assertEquals(PlaceDisplayTextResolver.FALLBACK_TITLE, resolved);
    }

    @Test
    public void resolveTitle_keepsNamedPlaceEvenWhenAddressStartsWithTheSameText() {
        String resolved = PlaceDisplayTextResolver.resolveTitle(
                null,
                "Truong Trung hoc pho thong chuyen",
                "Truong Trung hoc pho thong chuyen, 235 Nguyen Van Cu, Thu Duc");

        assertEquals("Truong Trung hoc pho thong chuyen", resolved);
    }

    @Test
    public void resolveAddress_stripsDuplicateTitleFromAddress() {
        String resolved = PlaceDisplayTextResolver.resolveAddress(
                "Truong Trung hoc pho thong chuyen",
                "Truong Trung hoc pho thong chuyen, 235 Nguyen Van Cu, Thu Duc");

        assertEquals("235 Nguyen Van Cu, Thu Duc", resolved);
    }

    @Test
    public void resolveAddress_returnsFallbackWhenAddressMissing() {
        String resolved = PlaceDisplayTextResolver.resolveAddress(
                PlaceDisplayTextResolver.FALLBACK_TITLE,
                null);

        assertEquals(PlaceDisplayTextResolver.FALLBACK_ADDRESS, resolved);
    }

    @Test
    public void hasMeaningfulTitle_rejectsGenericFallbackLabels() {
        assertFalse(PlaceDisplayTextResolver.hasMeaningfulTitle("Selected Location"));
        assertFalse(PlaceDisplayTextResolver.hasMeaningfulTitle(PlaceDisplayTextResolver.FALLBACK_TITLE));
        assertFalse(PlaceDisplayTextResolver.hasMeaningfulTitle(PlaceDisplayTextResolver.FALLBACK_ADDRESS));
        assertTrue(PlaceDisplayTextResolver.hasMeaningfulTitle("Truong Trung hoc pho thong chuyen"));
    }
}