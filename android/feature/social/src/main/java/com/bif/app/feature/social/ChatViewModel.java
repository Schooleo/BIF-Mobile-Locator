package com.bif.app.feature.social;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.domain.model.AiTripDraft;
import com.bif.app.domain.model.AiTripDraftResult;
import com.bif.app.domain.model.AiTripDraftStop;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.ITripRepository;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class ChatViewModel extends ViewModel {

    private final IChatRepository chatRepository;
    private final ITripRepository tripRepository;
    private final NetworkMonitor networkMonitor;

    private String groupId;
    private String groupName;
    private String currentUserId;

    private LiveData<List<ChatMessage>> messagesLiveData;
    private LiveData<List<TripPlan>> tripsLiveData;
    private final MutableLiveData<List<ChatMessage>> emptyMessagesLiveData =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<TripPlan>> emptyTripsLiveData =
            new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Set<String>> savedTripCardIdsLiveData =
            new MutableLiveData<>(new HashSet<>());
        private final MutableLiveData<Boolean> aiDraftModeEnabledLiveData =
            new MutableLiveData<>(false);
        private final MutableLiveData<Boolean> aiBadgesEnabledLiveData =
            new MutableLiveData<>(false);
        private final MutableLiveData<String> snackbarMessageLiveData =
            new MutableLiveData<>(null);
            private final Map<String, DraftTripCardSnapshot> pendingDraftTripSnapshots =
                Collections.synchronizedMap(new HashMap<>());

        private final Observer<Boolean> networkObserver = isOnline -> {
        boolean online = Boolean.TRUE.equals(isOnline);
        aiBadgesEnabledLiveData.postValue(online);
        if (!online) {
            aiDraftModeEnabledLiveData.postValue(false);
        }
        };

    @Inject
    public ChatViewModel(IChatRepository chatRepository,
                         ITripRepository tripRepository,
                         NetworkMonitor networkMonitor) {
        this.chatRepository = chatRepository;
        this.tripRepository = tripRepository;
        this.networkMonitor = networkMonitor;
        aiBadgesEnabledLiveData.setValue(networkMonitor.isOnline());
        networkMonitor.observeConnectivity().observeForever(networkObserver);
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

    public LiveData<Set<String>> getSavedTripCardIds() {
        return savedTripCardIdsLiveData;
    }

    public LiveData<Boolean> getAiDraftModeEnabled() {
        return aiDraftModeEnabledLiveData;
    }

    public LiveData<Boolean> getAiBadgesEnabled() {
        return aiBadgesEnabledLiveData;
    }

    public LiveData<String> getSnackbarMessage() {
        return snackbarMessageLiveData;
    }

    public void clearSnackbarMessage() {
        snackbarMessageLiveData.setValue(null);
    }

    public String getGroupName() {
        return groupName;
    }

    /** Send a plain text message. */
    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) return;
        if (groupId == null || groupId.trim().isEmpty()) return;

        if (Boolean.TRUE.equals(aiDraftModeEnabledLiveData.getValue())) {
            aiDraftModeEnabledLiveData.setValue(false);
            sendDraftTripQuery(content.trim());
            return;
        }

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

    public void enterAiDraftMode() {
        if (Boolean.TRUE.equals(aiDraftModeEnabledLiveData.getValue())) {
            aiDraftModeEnabledLiveData.setValue(false);
            return;
        }
        if (!networkMonitor.isOnline()) {
            aiDraftModeEnabledLiveData.setValue(false);
            snackbarMessageLiveData.setValue("AI drafting is unavailable while offline.");
            return;
        }
        aiDraftModeEnabledLiveData.setValue(true);
    }

    public void cancelAiDraftMode() {
        aiDraftModeEnabledLiveData.setValue(false);
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

    public void onSaveTripCard(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return;
        }

        DraftTripCardSnapshot snapshot = pendingDraftTripSnapshots.get(tripId);
        if (snapshot == null) {
            snackbarMessageLiveData.setValue("Unable to save this draft trip.");
            return;
        }

        tripRepository.saveDraftTrip(
                snapshot.tripId,
                snapshot.groupId,
                snapshot.title,
                snapshot.description,
                snapshot.startAt,
                snapshot.endAt,
                snapshot.stops,
                success -> {
                    if (!success) {
                        snackbarMessageLiveData.postValue("Failed to save trip. Please try again.");
                        return;
                    }

                    Set<String> current = savedTripCardIdsLiveData.getValue();
                    Set<String> updated = current == null
                            ? new HashSet<>()
                            : new HashSet<>(current);
                    updated.add(snapshot.tripId);
                    savedTripCardIdsLiveData.postValue(updated);
                    pendingDraftTripSnapshots.remove(snapshot.tripId);
                }
        );
    }

    public boolean isTripCardSaved(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) return false;
        Set<String> savedIds = savedTripCardIdsLiveData.getValue();
        return savedIds != null && savedIds.contains(tripId);
    }

    public void addSuggestedPlaceToTrip(String tripId, Place place) {
        if (tripId == null || tripId.trim().isEmpty() || place == null) return;

        if (place.location == null
            || !Double.isFinite(place.location.latitude)
            || !Double.isFinite(place.location.longitude)) {
            return;
        }

        TripStop stop = new TripStop(
                UUID.randomUUID().toString(),
                place.name,
                place.address,
            place.location.latitude,
            place.location.longitude,
                0L,
                0L,
                0
        );
        tripRepository.addStopToTrip(tripId, stop);
    }

    /** Manually refresh messages from the server (used after network restore). */
    public void refreshMessages() {
        if (groupId != null) {
            chatRepository.refreshMessages(groupId);
        }
    }

    private void sendDraftTripQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        LiveData<AiTripDraftResult> source = chatRepository.draftTripFromQuery(query);
        observeOnce(source, result -> {
            String failureCode = result != null ? result.getFailureCode() : "AI_FAILURE";
            if (failureCode != null) {
                aiDraftModeEnabledLiveData.postValue(false);
                snackbarMessageLiveData.postValue("AI draft failed (" + failureCode + ")");
                return;
            }

            ChatMessage draftCard = buildDraftCardMessage(result);
            if (draftCard == null) {
                aiDraftModeEnabledLiveData.postValue(false);
                snackbarMessageLiveData.postValue("Unable to build AI draft result.");
                return;
            }
            chatRepository.insertLocalMessage(draftCard);
        });
    }

    private ChatMessage buildDraftCardMessage(AiTripDraftResult result) {
        if (result == null || result.getDraft() == null) {
            return null;
        }

        AiTripDraft draft = result.getDraft();
        List<AiTripDraftStop> stops = draft.getStops();
        int stopCount = stops != null ? stops.size() : 0;

        String draftTripId = "ai-draft-" + UUID.randomUUID();
        List<TripStop> draftStops = toTripStops(stops);
        pendingDraftTripSnapshots.put(draftTripId, new DraftTripCardSnapshot(
            draftTripId,
            groupId,
            draft.getTitle() != null ? draft.getTitle() : "AI Draft Trip",
            draft.getSummary() != null ? draft.getSummary() : "",
            System.currentTimeMillis(),
            0L,
            draftStops
        ));
        String payload = buildDraftPayloadJson(draftTripId, draft, stops, result.getCandidatePlaces(), stopCount);

        return new ChatMessage(
                UUID.randomUUID().toString(),
                groupId,
                currentUserId,
                null,
                payload.toString(),
                "TRIP_CREATED_CARD",
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                0,
                0,
                null,
                false,
                true
        );
    }

    private String buildDraftPayloadJson(String draftTripId,
                                         AiTripDraft draft,
                                         List<AiTripDraftStop> stops,
                                         List<Place> candidates,
                                         int stopCount) {
        long startTime = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"tripId\":").append(jsonString(draftTripId)).append(",");
        sb.append("\"stopCount\":").append(stopCount).append(",");
        sb.append("\"startTime\":").append(startTime).append(",");
        sb.append("\"isSaved\":false,");
        sb.append("\"totalDistance\":0.0,");
        sb.append("\"title\":").append(jsonString(draft.getTitle() != null ? draft.getTitle() : "AI Draft Trip")).append(",");
        sb.append("\"summary\":").append(jsonString(draft.getSummary() != null ? draft.getSummary() : "")).append(",");
        sb.append("\"stops\":[");

        if (stops != null) {
            boolean firstStop = true;
            for (AiTripDraftStop stop : stops) {
                if (stop == null) continue;
                if (!firstStop) {
                    sb.append(",");
                }
                firstStop = false;

                Place place = stop.getPlace();
                Location location = place != null ? place.location : null;

                sb.append("{");
                sb.append("\"placeId\":").append(jsonString(stop.getPlaceId())).append(",");
                sb.append("\"durationMinutes\":").append(Math.max(0, stop.getDurationMinutes())).append(",");
                sb.append("\"note\":").append(jsonString(stop.getNote() != null ? stop.getNote() : "")).append(",");
                sb.append("\"name\":").append(jsonString(place != null ? place.name : "")).append(",");
                sb.append("\"address\":").append(jsonString(place != null ? place.address : "")).append(",");
                sb.append("\"latitude\":");
                appendNullableDouble(sb, location != null ? location.latitude : null);
                sb.append(",");
                sb.append("\"longitude\":");
                appendNullableDouble(sb, location != null ? location.longitude : null);
                sb.append("}");
            }
        }

        sb.append("],");
        sb.append("\"candidatePlaces\":[");
        if (candidates != null) {
            boolean firstCandidate = true;
            for (Place place : candidates) {
                if (place == null) continue;
                if (!firstCandidate) {
                    sb.append(",");
                }
                firstCandidate = false;
                Location location = place.location;
                sb.append("{");
                sb.append("\"id\":").append(jsonString(place.id)).append(",");
                sb.append("\"name\":").append(jsonString(place.name)).append(",");
                sb.append("\"address\":").append(jsonString(place.address)).append(",");
                sb.append("\"rating\":").append(place.rating).append(",");
                sb.append("\"latitude\":");
                appendNullableDouble(sb, location != null ? location.latitude : null);
                sb.append(",");
                sb.append("\"longitude\":");
                appendNullableDouble(sb, location != null ? location.longitude : null);
                sb.append("}");
            }
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
    }

    private String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                default:
                    if (ch <= 0x1F) {
                        escaped.append(String.format("\\u%04x", (int) ch));
                    } else {
                        escaped.append(ch);
                    }
                    break;
            }
        }
        escaped.append('"');
        return escaped.toString();
    }

    private List<TripStop> toTripStops(List<AiTripDraftStop> stops) {
        List<TripStop> mapped = new ArrayList<>();
        if (stops == null) {
            return mapped;
        }

        int orderIndex = 0;
        for (AiTripDraftStop stop : stops) {
            if (stop == null) {
                continue;
            }

            Place place = stop.getPlace();
            Location location = place != null ? place.location : null;
                boolean hasValidCoordinates = location != null
                    && Double.isFinite(location.latitude)
                    && Double.isFinite(location.longitude);
                if (!hasValidCoordinates) {
                continue;
                }
                double latitude = location.latitude;
                double longitude = location.longitude;

            String stopTitle = place != null && place.name != null
                    ? place.name
                    : "";
            String stopAddress = place != null && place.address != null
                    ? place.address
                    : "";

            mapped.add(new TripStop(
                    UUID.randomUUID().toString(),
                    stopTitle,
                    stopAddress,
                    stop.getNote(),
                    latitude,
                    longitude,
                    0L,
                    0L,
                    orderIndex
            ));
            orderIndex++;
        }
        return mapped;
    }

    private void appendNullableDouble(StringBuilder sb, Double value) {
        if (value == null || !Double.isFinite(value)) {
            sb.append("null");
            return;
        }
        sb.append(value);
    }

    private static class DraftTripCardSnapshot {
        final String tripId;
        final String groupId;
        final String title;
        final String description;
        final long startAt;
        final long endAt;
        final List<TripStop> stops;

        DraftTripCardSnapshot(String tripId,
                              String groupId,
                              String title,
                              String description,
                              long startAt,
                              long endAt,
                              List<TripStop> stops) {
            this.tripId = tripId;
            this.groupId = groupId;
            this.title = title;
            this.description = description;
            this.startAt = startAt;
            this.endAt = endAt;
            this.stops = stops;
        }
    }

    private <T> void observeOnce(LiveData<T> source, Observer<T> observer) {
        source.observeForever(new Observer<T>() {
            @Override
            public void onChanged(T value) {
                source.removeObserver(this);
                observer.onChanged(value);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        networkMonitor.observeConnectivity().removeObserver(networkObserver);
        chatRepository.disconnectFromGroup();
    }
}
