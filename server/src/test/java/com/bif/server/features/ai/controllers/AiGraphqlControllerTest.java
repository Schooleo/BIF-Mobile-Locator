package com.bif.server.features.ai.controllers;

import com.bif.server.features.ai.dto.graphql.AiPlaceSuggestionResult;
import com.bif.server.features.ai.dto.graphql.AiTripDraftResult;
import com.bif.server.features.ai.services.AiOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGraphqlControllerTest {

    @Mock
    private AiOrchestratorService aiOrchestratorService;

    private AiGraphqlController controller;

    @BeforeEach
    void setUp() {
        controller = new AiGraphqlController(aiOrchestratorService);
    }

    @Test
    void suggestPlacesFromQuery_DelegatesToService() {
        AiPlaceSuggestionResult result = new AiPlaceSuggestionResult(
                List.of(),
                List.of("coffee"),
                "cafe",
                "quiet",
                List.of(),
                null
        );
        when(aiOrchestratorService.suggestPlacesFromQuery("coffee"))
                .thenReturn(result);

        AiPlaceSuggestionResult actual = controller.suggestPlacesFromQuery("coffee");

        assertSame(result, actual);
        verify(aiOrchestratorService).suggestPlacesFromQuery("coffee");
    }

    @Test
    void draftTripFromQuery_DelegatesToService() {
        AiTripDraftResult result = new AiTripDraftResult(
                null,
                List.of(),
                List.of("warning"),
                null
        );
        when(aiOrchestratorService.draftTripFromQuery("day trip"))
                .thenReturn(result);

        AiTripDraftResult actual = controller.draftTripFromQuery("day trip");

        assertSame(result, actual);
        verify(aiOrchestratorService).draftTripFromQuery("day trip");
    }
}
