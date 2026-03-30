package com.bif.server.features.trip.services;

import com.bif.server.features.chat.models.ChatMessage;
import com.bif.server.features.chat.services.ChatService;
import com.bif.server.features.trip.models.TripPlan;
import com.bif.server.features.trip.models.TripStop;
import org.springframework.stereotype.Service;

@Service
public class TripActivityService {
    private final ChatService chatService;

    public TripActivityService(ChatService chatService) {
        this.chatService = chatService;
    }

    public void postTripCreated(TripPlan plan, String userId) {
        postSystemMessage(plan.getGroupId(),
                String.format("\uD83D\uDDFA\uFE0F Trip '%s' was created", plan.getTitle()),
                userId);
    }

    public void postTripUpdated(TripPlan plan, String userId) {
        postSystemMessage(plan.getGroupId(),
                String.format("\uD83D\uDDFA\uFE0F Trip '%s' was updated", plan.getTitle()),
                userId);
    }

    public void postTripDeleted(String groupId, String tripTitle, String userId) {
        postSystemMessage(groupId,
                String.format("\uD83D\uDDD1\uFE0F Trip '%s' was deleted", tripTitle),
                userId);
    }

    public void postStopAdded(TripPlan plan, TripStop stop, String userId) {
        postSystemMessage(plan.getGroupId(),
                String.format("\uD83D\uDCCD Stop '%s' was added to trip '%s'",
                        stop.getTitle(), plan.getTitle()),
                userId);
    }

    public void postStopRemoved(TripPlan plan, String stopId, String userId) {
        postSystemMessage(plan.getGroupId(),
                String.format("\u274C A stop was removed from trip '%s'",
                        plan.getTitle()),
                userId);
    }

    public void postStopsRearranged(TripPlan plan, String userId) {
        postSystemMessage(plan.getGroupId(),
                String.format("\uD83D\uDD00 Stops in trip '%s' were rearranged",
                        plan.getTitle()),
                userId);
    }

    private void postSystemMessage(String groupId, String content, String userId) {
        ChatMessage msg = new ChatMessage();
        msg.setGroupId(groupId);
        msg.setSenderUserId(userId);
        msg.setContent(content);
        msg.setType("SYSTEM");
        chatService.save(msg);
    }
}
