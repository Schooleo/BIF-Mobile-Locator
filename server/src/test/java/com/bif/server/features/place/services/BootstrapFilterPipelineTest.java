package com.bif.server.features.place.services;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapFilterPipelineTest {

    private final BootstrapFilterPipeline pipeline = new BootstrapFilterPipeline(
            true,
            true,
            true,
            true
    );

    @Test
    void evaluate_rejectsCoordinatesOutsideVietnamBoundingBox() {
        BootstrapFilterPipeline.FilterDecision decision = pipeline.evaluate(
                new BootstrapFilterPipeline.Candidate(
                        "Valid Name",
                        Set.of("tourism", "museum"),
                        "VN",
                        35.0,
                        120.0
                )
        );

        assertEquals(BootstrapFilterPipeline.RejectReason.OUTSIDE_VIETNAM_BBOX, decision.reason());
    }

    @Test
    void evaluate_rejectsNullIslandCoordinates() {
        BootstrapFilterPipeline.FilterDecision decision = pipeline.evaluate(
                new BootstrapFilterPipeline.Candidate(
                        "Valid Name",
                        Set.of("tourism", "attraction"),
                        "VN",
                        0.0,
                        0.0
                )
        );

        assertEquals(BootstrapFilterPipeline.RejectReason.NULL_ISLAND_COORDINATES, decision.reason());
    }

    @Test
    void evaluate_rejectsMissingAmenityOrTourismSemanticTags() {
        BootstrapFilterPipeline.FilterDecision decision = pipeline.evaluate(
                new BootstrapFilterPipeline.Candidate(
                        "A Place",
                        Set.of("office", "industrial"),
                        "VN",
                        10.8,
                        106.7
                )
        );

        assertEquals(BootstrapFilterPipeline.RejectReason.MISSING_AMENITY_OR_TOURISM_TAG, decision.reason());
    }

    @Test
    void evaluate_rejectsJunkNameByRegex() {
        BootstrapFilterPipeline.FilterDecision decision = pipeline.evaluate(
                new BootstrapFilterPipeline.Candidate(
                        "N/A",
                        Set.of("tourism", "museum"),
                        "VN",
                        10.8,
                        106.7
                )
        );

        assertEquals(BootstrapFilterPipeline.RejectReason.JUNK_NAME, decision.reason());
    }

    @Test
    void evaluate_acceptsValidVietnamPlace() {
        BootstrapFilterPipeline.FilterDecision decision = pipeline.evaluate(
                new BootstrapFilterPipeline.Candidate(
                        "Bảo tàng Mỹ thuật",
                        Set.of("tourism", "museum", "gallery"),
                        "VN",
                        10.772,
                        106.698
                )
        );

        assertTrue(decision.accepted());
    }
}

