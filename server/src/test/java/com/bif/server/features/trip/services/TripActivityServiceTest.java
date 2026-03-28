package com.bif.server.features.trip.services;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripActivityServiceTest {

    @Mock
    private ChatService chatService;

    private TripActivityService tripActivityService;

    @BeforeEach
    void setUp() {
        tripActivityService = new TripActivityService(chatService);
    }

    @Test
    void postTripCreated_PostsSystemMessage() {
        TripPlan plan = new TripPlan();
        plan.setGroupId("g1");
        plan.setTitle("Weekend Trip");

        when(chatService.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        tripActivityService.postTripCreated(plan, "u1");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        ChatMessage msg = captor.getValue();
        assertEquals("g1", msg.getGroupId());
        assertEquals("u1", msg.getSenderUserId());
        assertEquals("SYSTEM", msg.getType());
        assertTrue(msg.getContent().contains("Weekend Trip"));
        assertTrue(msg.getContent().contains("created"));
    }

    @Test
    void postTripUpdated_PostsSystemMessage() {
        TripPlan plan = new TripPlan();
        plan.setGroupId("g1");
        plan.setTitle("Weekend Trip");

        when(chatService.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        tripActivityService.postTripUpdated(plan, "u1");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("updated"));
    }

    @Test
    void postTripDeleted_PostsSystemMessage() {
        when(chatService.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        tripActivityService.postTripDeleted("g1", "Weekend Trip", "u1");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertEquals("g1", captor.getValue().getGroupId());
        assertTrue(captor.getValue().getContent().contains("deleted"));
    }

    @Test
    void postStopAdded_PostsSystemMessage() {
        TripPlan plan = new TripPlan();
        plan.setGroupId("g1");
        plan.setTitle("Weekend Trip");
        TripStop stop = new TripStop();
        stop.setTitle("Central Park");

        when(chatService.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        tripActivityService.postStopAdded(plan, stop, "u1");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("Central Park"));
        assertTrue(captor.getValue().getContent().contains("Weekend Trip"));
    }

    @Test
    void postStopRemoved_PostsSystemMessage() {
        TripPlan plan = new TripPlan();
        plan.setGroupId("g1");
        plan.setTitle("Weekend Trip");

        when(chatService.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        tripActivityService.postStopRemoved(plan, "s1", "u1");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("removed"));
    }

    @Test
    void postStopsRearranged_PostsSystemMessage() {
        TripPlan plan = new TripPlan();
        plan.setGroupId("g1");
        plan.setTitle("Weekend Trip");

        when(chatService.save(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

        tripActivityService.postStopsRearranged(plan, "u1");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatService).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("rearranged"));
    }
}
