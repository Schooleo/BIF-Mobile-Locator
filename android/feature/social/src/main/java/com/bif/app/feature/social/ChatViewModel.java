package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.ITripRepository;

import java.util.List;
import java.util.Collections;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ChatViewModel extends ViewModel {

    private final IChatRepository chatRepository;
    private final ITripRepository tripRepository;

    private String groupId;
    private String groupName;
    private String currentUserId;

    private LiveData<List<ChatMessage>> messagesLiveData;
    private LiveData<List<TripPlan>> tripsLiveData;
        private final MutableLiveData<List<ChatMessage>> emptyMessagesLiveData =
            new MutableLiveData<>(Collections.emptyList());
        private final MutableLiveData<List<TripPlan>> emptyTripsLiveData =
            new MutableLiveData<>(Collections.emptyList());

    @Inject
    public ChatViewModel(IChatRepository chatRepository, ITripRepository tripRepository) {
        this.chatRepository = chatRepository;
        this.tripRepository = tripRepository;
    }

    /**
     * Must be called once when the Fragment is ready.
     * Loads messages, refreshes from server, and starts WebSocket connection.
     */
    public void init(String groupId, String groupName, String userId) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.currentUserId = userId;

        if (groupId == null || groupId.trim().isEmpty()) {
            messagesLiveData = emptyMessagesLiveData;
            tripsLiveData = emptyTripsLiveData;
            return;
        }

        messagesLiveData = chatRepository.getMessagesByGroup(groupId);
        tripsLiveData = tripRepository.getTripsByGroup(groupId);

        chatRepository.refreshMessages(groupId);
        tripRepository.refreshTrips(groupId);
        chatRepository.connectToGroup(groupId);
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messagesLiveData != null ? messagesLiveData : emptyMessagesLiveData;
    }

    public LiveData<List<TripPlan>> getTrips() {
        return tripsLiveData != null ? tripsLiveData : emptyTripsLiveData;
    }

    public String getGroupName() {
        return groupName;
    }

    /** Send a plain text message. */
    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;
        if (groupId == null || groupId.trim().isEmpty()) return;

        String id = UUID.randomUUID().toString();
        String clientMsgId = UUID.randomUUID().toString();
        ChatMessage message = new ChatMessage(
                id, groupId, currentUserId,
                null, content.trim(), "TEXT",
                System.currentTimeMillis(), clientMsgId,
                0, 0, null, false, true
        );
        chatRepository.sendMessage(message);
    }

    /** Share a location as a LOCATION-type message. */
    public void shareLocation(double latitude, double longitude, String address) {
        if (groupId == null || groupId.trim().isEmpty()) return;
        chatRepository.sendLocationMessage(groupId, currentUserId, latitude, longitude, address);
    }

    /** Add a shared location from a chat message to an existing trip plan. */
    public void addSharedLocationToTrip(String tripId, ChatMessage message) {
        if (!message.isLocationMessage()) return;

        TripStop stop = new TripStop(
                UUID.randomUUID().toString(),
                message.getContent() != null && !message.getContent().isEmpty()
                        ? message.getContent() : message.getSharedAddress(),
                message.getSharedAddress(),
                message.getSharedLatitude(),
                message.getSharedLongitude(),
                0L, 0L, 0
        );
        tripRepository.addStopToTrip(tripId, stop);
    }

    /** Manually refresh messages from the server (used after network restore). */
    public void refreshMessages() {
        if (groupId != null) {
            chatRepository.refreshMessages(groupId);
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        chatRepository.disconnectFromGroup();
    }
}
