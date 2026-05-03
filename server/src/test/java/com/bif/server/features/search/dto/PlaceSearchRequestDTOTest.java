package com.bif.server.features.search.dto;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlaceSearchRequestDTOTest {

    @ParameterizedTest
    @CsvSource(value = {
            "null, 15",
            "0, 15",
            "-10, 15",
            "1, 1",
            "25, 25",
            "50, 50",
            "51, 50",
            "200, 50"
    }, nullValues = "null")
    void getPerPage_ReturnsExpectedValueForBoundaryAndTypicalInputs(
            Integer requestedPerPage,
            int expectedPerPage
    ) {
        PlaceSearchRequestDTO request = new PlaceSearchRequestDTO();

        request.setPerPage(requestedPerPage);

        assertEquals(expectedPerPage, request.getPerPage());
    }
}
