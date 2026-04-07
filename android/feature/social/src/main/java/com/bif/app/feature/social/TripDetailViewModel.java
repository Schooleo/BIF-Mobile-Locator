package com.bif.app.feature.social;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.utils.ChatReadStateStore;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.ITripRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class TripDetailViewModel extends ViewModel {

    private final ITripRepository tripRepository;
    private final IChatRepository chatRepository;
    private final Context appContext;
    private LiveData<TripPlan> trip;
    private LiveData<List<ChatMessage>> groupMessages;
    private String currentTripId = "";
    private final MediatorLiveData<Boolean> hasUnreadGroupMessages = new MediatorLiveData<>();
    private final MutableLiveData<Integer> unreadRefreshToken = new MutableLiveData<>(0);

    @Inject
    public TripDetailViewModel(ITripRepository tripRepository,
                               IChatRepository chatRepository,
                               @ApplicationContext Context appContext) {
        this.tripRepository = tripRepository;
        this.chatRepository = chatRepository;
        this.appContext = appContext;
        hasUnreadGroupMessages.setValue(false);
        hasUnreadGroupMessages.addSource(unreadRefreshToken, token -> recomputeUnreadState());
    }

    public void loadTrip(String tripId) {
        currentTripId = tripId == null ? "" : tripId;
        trip = tripRepository.getTripById(currentTripId);

        if (groupMessages != null) {
            hasUnreadGroupMessages.removeSource(groupMessages);
        }
        if (currentTripId.trim().isEmpty()) {
            groupMessages = null;
            hasUnreadGroupMessages.setValue(false);
            return;
        }

        groupMessages = chatRepository.getMessagesByGroup(currentTripId);
        hasUnreadGroupMessages.addSource(groupMessages, messages -> recomputeUnreadState());
        refreshUnreadState();
    }

    public LiveData<TripPlan> getTrip() {
        return trip;
    }

    public String getCurrentTripId() {
        return currentTripId;
    }

    public LiveData<Boolean> getHasUnreadGroupMessages() {
        return hasUnreadGroupMessages;
    }

    public void markGroupChatReadNow() {
        if (currentTripId == null || currentTripId.trim().isEmpty()) {
            return;
        }
        ChatReadStateStore.markGroupReadNow(appContext, currentTripId);
        refreshUnreadState();
    }

    public void refreshUnreadState() {
        Integer token = unreadRefreshToken.getValue();
        unreadRefreshToken.setValue(token == null ? 1 : token + 1);
    }

    public void removeStop(String stopId) {
        if (currentTripId == null || currentTripId.trim().isEmpty()
                || stopId == null || stopId.trim().isEmpty()) {
            return;
        }
        tripRepository.removeStopFromTrip(currentTripId, stopId);
    }

    public void updateStopSchedule(TripStop stop, long scheduledAtMillis) {
        if (currentTripId == null || currentTripId.trim().isEmpty() || stop == null) {
            return;
        }
        String targetId = stop.getId();
        if (targetId == null) {
            return;
        }

        List<TripStop> updatedStops = rebuildStopsWithUpdate(targetId, item -> new TripStop(
                item.getId(),
                item.getTitle(),
                item.getAddress(),
                item.getNote(),
                item.getPhotoUrl(),
                item.getLocalImagePath(),
                item.getLatitude(),
                item.getLongitude(),
                scheduledAtMillis,
                scheduledAtMillis,
                item.getOrderIndex()));
        if (updatedStops == null) {
            return;
        }

        tripRepository.rearrangeStopsInTrip(currentTripId, updatedStops);
    }

    public void updateStopDetails(TripStop stop, String note, long scheduledAtMillis) {
        if (currentTripId == null || currentTripId.trim().isEmpty() || stop == null) {
            return;
        }
        String targetId = stop.getId();
        if (targetId == null) {
            return;
        }

        String nextNote = note == null ? "" : note.trim();
        List<TripStop> updatedStops = rebuildStopsWithUpdate(targetId, item -> new TripStop(
                item.getId(),
                item.getTitle(),
                item.getAddress(),
                nextNote,
                item.getPhotoUrl(),
                item.getLocalImagePath(),
                item.getLatitude(),
                item.getLongitude(),
                scheduledAtMillis,
                scheduledAtMillis,
                item.getOrderIndex()));
        if (updatedStops == null) {
            return;
        }

        tripRepository.rearrangeStopsInTrip(currentTripId, updatedStops);
    }

    private List<TripStop> rebuildStopsWithUpdate(@NonNull String targetId, @NonNull StopUpdater updater) {
        TripPlan currentTrip = trip == null ? null : trip.getValue();
        if (currentTrip == null || currentTrip.getStops() == null || currentTrip.getStops().isEmpty()) {
            return null;
        }

        List<TripStop> updatedStops = new ArrayList<>();
        for (TripStop item : currentTrip.getStops()) {
            if (item != null && targetId.equals(item.getId())) {
                updatedStops.add(updater.apply(item));
            } else {
                updatedStops.add(item);
            }
        }
        return updatedStops;
    }

    private interface StopUpdater {
        @NonNull
        TripStop apply(@NonNull TripStop original);
    }

    private void recomputeUnreadState() {
        if (currentTripId == null || currentTripId.trim().isEmpty()) {
            hasUnreadGroupMessages.setValue(false);
            return;
        }

        long lastReadAt = ChatReadStateStore.getGroupLastReadAt(appContext, currentTripId);
        List<ChatMessage> messages = groupMessages == null ? null : groupMessages.getValue();
        if (messages == null || messages.isEmpty()) {
            hasUnreadGroupMessages.setValue(false);
            return;
        }

        boolean hasUnread = false;
        for (ChatMessage message : messages) {
            if (message == null || message.isOutgoing()) {
                continue;
            }
            if (message.getSentAt() > lastReadAt) {
                hasUnread = true;
                break;
            }
        }
        hasUnreadGroupMessages.setValue(hasUnread);
    }
}

