package com.bif.app.feature.social;

import android.util.Log;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.bif.app.core.utils.UserPreferences;
import com.bif.app.data.sync.core.NetworkMonitor;
import com.bif.app.domain.model.AiPlaceSuggestion;
import com.bif.app.domain.model.AiPlaceSuggestionResult;
import com.bif.app.domain.model.AiTripDraft;
import com.bif.app.domain.model.AiTripDraftResult;
import com.bif.app.domain.model.AiTripDraftStop;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.bif.app.domain.repository.IChatRepository;
import com.bif.app.domain.repository.IPlaceRepository;
import com.bif.app.domain.repository.ITripRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class ChatViewModel extends ViewModel {

    private static final String AI_SENDER_USER_ID = "ai-assistant";
    private static final String AI_SENDER_NAME = "AI Trip Drafter";
    private static final String AI_DRAFTING_MESSAGE = "Drafting a trip...";
    private static final String AI_DRAFTED_MESSAGE = "Drafted trip";
    private static final String AI_DRAFT_ERROR_PREFIX = "Errors drafting trip: ";
    private static final String AI_SUGGESTING_MESSAGE = "Suggesting places...";
    private static final String AI_SUGGESTED_MESSAGE = "Suggested places";
    private static final String AI_SUGGEST_ERROR_PREFIX = "Errors suggesting places: ";

    private final IChatRepository chatRepository;
    private final IPlaceRepository placeRepository;
    private final ITripRepository tripRepository;
    private final NetworkMonitor networkMonitor;
    private final Context appContext;

    private String groupId;
    private String groupName;
    private String currentUserId;

    private LiveData<List<ChatMessage>> messagesLiveData;
    private LiveData<List<TripPlan>> tripsLiveData;
    private final MutableLiveData<List<ChatMessage>> emptyMessagesLiveData
            = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<TripPlan>> emptyTripsLiveData
            = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Set<String>> savedTripCardIdsLiveData
            = new MutableLiveData<>(new HashSet<>());
    private final MutableLiveData<Boolean> aiDraftModeEnabledLiveData
            = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> aiSuggestPlacesModeEnabledLiveData
            = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> aiBadgesEnabledLiveData
            = new MutableLiveData<>(false);
    private final MutableLiveData<String> snackbarMessageLiveData
            = new MutableLiveData<>(null);
    private final Map<LiveData<?>, Set<Observer<?>>> observeOnceObservers
            = Collections.synchronizedMap(new java.util.HashMap<>());

    private final Observer<Boolean> networkObserver = isOnline -> {
        boolean online = Boolean.TRUE.equals(isOnline);
        aiBadgesEnabledLiveData.postValue(online);
        if (!online) {
            aiDraftModeEnabledLiveData.postValue(false);
            aiSuggestPlacesModeEnabledLiveData.postValue(false);
        }
    };

    @Inject
    public ChatViewModel(IChatRepository chatRepository,
            IPlaceRepository placeRepository,
            ITripRepository tripRepository,
            NetworkMonitor networkMonitor,
            @ApplicationContext Context appContext) {
        this.chatRepository = chatRepository;
        this.placeRepository = placeRepository;
        this.tripRepository = tripRepository;
        this.networkMonitor = networkMonitor;
        this.appContext = appContext;
        aiBadgesEnabledLiveData.setValue(networkMonitor.isOnline());
        networkMonitor.observeConnectivity().observeForever(networkObserver);
    }

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

    public LiveData<Set<String>> getSavedTripCardIds() {
        return savedTripCardIdsLiveData;
    }

    public LiveData<List<TripPlan>> getTrips() {
        return tripsLiveData != null ? tripsLiveData : emptyTripsLiveData;
    }

    public LiveData<Boolean> getAiDraftModeEnabled() {
        return aiDraftModeEnabledLiveData;
    }

    public LiveData<Boolean> getAiSuggestPlacesModeEnabled() {
        return aiSuggestPlacesModeEnabledLiveData;
    }

    public LiveData<Boolean> getAiBadgesEnabled() {
        return aiBadgesEnabledLiveData;
    }

    public boolean isAiAvailable() {
        if (!isAuthenticated()) {
            return false;
        }
        Boolean available = aiBadgesEnabledLiveData.getValue();
        return available != null ? available : networkMonitor.isOnline();
    }

    public boolean isAuthenticated() {
        String token = UserPreferences.getAuthToken(appContext);
        return UserPreferences.isLoggedIn(appContext)
                && !trim(token).isEmpty();
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

    public void sendMessage(String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        if (groupId == null || groupId.trim().isEmpty()) {
            return;
        }

        if (Boolean.TRUE.equals(aiDraftModeEnabledLiveData.getValue())) {
            aiDraftModeEnabledLiveData.setValue(false);
            sendDraftTripQuery(content.trim());
            return;
        }
        if (Boolean.TRUE.equals(aiSuggestPlacesModeEnabledLiveData.getValue())) {
            aiSuggestPlacesModeEnabledLiveData.setValue(false);
            sendSuggestedPlacesQuery(content.trim());
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
        if (!isAuthenticated()) {
            aiDraftModeEnabledLiveData.setValue(false);
            snackbarMessageLiveData.setValue("Please log in to use AI trip drafting.");
            return;
        }
        if (!networkMonitor.isOnline()) {
            aiDraftModeEnabledLiveData.setValue(false);
            snackbarMessageLiveData.setValue("AI drafting is unavailable while offline.");
            return;
        }
        aiSuggestPlacesModeEnabledLiveData.setValue(false);
        aiDraftModeEnabledLiveData.setValue(true);
    }

    public void cancelAiDraftMode() {
        aiDraftModeEnabledLiveData.setValue(false);
    }

    public void enterAiSuggestPlacesMode() {
        if (Boolean.TRUE.equals(aiSuggestPlacesModeEnabledLiveData.getValue())) {
            aiSuggestPlacesModeEnabledLiveData.setValue(false);
            return;
        }
        if (!isAuthenticated()) {
            aiSuggestPlacesModeEnabledLiveData.setValue(false);
            snackbarMessageLiveData.setValue("Please log in to use AI place suggestions.");
            return;
        }
        if (!networkMonitor.isOnline()) {
            aiSuggestPlacesModeEnabledLiveData.setValue(false);
            snackbarMessageLiveData.setValue("AI place suggestions are unavailable while offline.");
            return;
        }
        aiDraftModeEnabledLiveData.setValue(false);
        aiSuggestPlacesModeEnabledLiveData.setValue(true);
    }

    public void cancelAiSuggestPlacesMode() {
        aiSuggestPlacesModeEnabledLiveData.setValue(false);
    }

    public void sharePlaceCard(String placeId,
                               String name,
                               String address,
                               double latitude,
                               double longitude,
                               double rating,
                               String placeSource) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return;
        }
        
        try {
            org.json.JSONObject payload = new org.json.JSONObject();
            payload.put("id", placeId);
            payload.put("name", name);
            payload.put("address", address);
            payload.put("latitude", latitude);
            payload.put("longitude", longitude);
            payload.put("rating", rating);
            payload.put("placeSource", placeSource);
            
            String id = UUID.randomUUID().toString();
            String clientMsgId = UUID.randomUUID().toString();
            ChatMessage message = new ChatMessage(
                    id, groupId, currentUserId,
                    null, payload.toString(), "PLACE_SHARE_CARD",
                    System.currentTimeMillis(), clientMsgId,
                    latitude, longitude, address, false, true
            );
            chatRepository.sendMessage(message);
        } catch (Exception e) {
            Log.e("ChatViewModel", "Failed to create place share payload", e);
        }
    }

    public void addSharedLocationToTrip(String tripId, ChatMessage message) {
        if (!message.isLocationMessage()) {
            return;
        }

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

    public boolean isCurrentUserHostForCurrentTrip() {
        TripPlan trip = resolveCurrentGroupTrip();
        if (trip == null || trip.getParticipantIds() == null || trip.getParticipantIds().isEmpty()) {
            return false;
        }

        String ownerId = trim(trip.getParticipantIds().get(0));
        if (ownerId.isEmpty()) {
            return false;
        }
        return ownerId.equals(trim(currentUserId));
    }

    public boolean hasCurrentTripForOverride() {
        String tripId = getCurrentTripId();
        return !tripId.isEmpty();
    }

    public String getCurrentTripId() {
        TripPlan trip = resolveCurrentGroupTrip();
        if (trip == null) {
            return "";
        }
        return trim(trip.getId());
    }

    public void onSaveTripCardAsNew(String draftTripId, String payloadJson) {
        if (!isCurrentUserHostForCurrentTrip()) {
            snackbarMessageLiveData.setValue("Only the trip host can save this AI draft.");
            return;
        }
        applyDraftTripPayload(draftTripId, payloadJson, false);
    }

    public void onOverrideTripCard(String draftTripId, String payloadJson) {
        if (!isCurrentUserHostForCurrentTrip()) {
            snackbarMessageLiveData.setValue("Only the trip host can override the current trip.");
            return;
        }
        applyDraftTripPayload(draftTripId, payloadJson, true);
    }

    public boolean isTripCardSaved(String tripId) {
        if (tripId == null || tripId.trim().isEmpty()) {
            return false;
        }
        Set<String> savedIds = savedTripCardIdsLiveData.getValue();
        return savedIds != null && savedIds.contains(tripId);
    }

    private void applyDraftTripPayload(String draftTripId,
                                       String payloadJson,
                                       boolean overrideCurrentTrip) {
        DraftTripCardSnapshot snapshot = parseDraftTripPayload(payloadJson, draftTripId);
        if (snapshot == null) {
            snackbarMessageLiveData.setValue("Unable to parse this draft trip.");
            return;
        }
        if (snapshot.stops.isEmpty()) {
            snackbarMessageLiveData.setValue("This draft has no valid stops to save.");
            return;
        }

        String targetTripId;
        if (overrideCurrentTrip) {
            targetTripId = resolveOverrideTargetTripId(snapshot.currentTripId);
            if (targetTripId.isEmpty()) {
                snackbarMessageLiveData.setValue("No current trip available to override.");
                return;
            }
        } else {
            targetTripId = !snapshot.draftTripId.isEmpty()
                    ? snapshot.draftTripId
                    : ("ai-draft-" + UUID.randomUUID());
        }

        long startAt;
        long endAt;
        if (snapshot.startAt > 0L && snapshot.endAt > snapshot.startAt) {
            startAt = snapshot.startAt;
            endAt = snapshot.endAt;
        } else {
            startAt = resolveStartAt(snapshot.stops, snapshot.startAt);
            endAt = resolveEndAt(snapshot.stops, startAt, snapshot.endAt);
        }
        tripRepository.saveDraftTrip(
                targetTripId,
                trim(groupId),
                snapshot.title,
                snapshot.description,
                startAt,
                endAt,
                snapshot.stops,
                success -> {
                    if (!success) {
                        snackbarMessageLiveData.postValue("Failed to save trip. Please try again.");
                        return;
                    }
                    markDraftCardSaved(snapshot.draftTripId);
                    snackbarMessageLiveData.postValue(overrideCurrentTrip
                            ? "Current trip updated from AI draft."
                            : "AI draft saved as a new trip.");
                    tripRepository.refreshTrips(trim(groupId));
                }
        );
    }

    private void markDraftCardSaved(String draftTripId) {
        String normalized = trim(draftTripId);
        if (normalized.isEmpty()) {
            return;
        }
        Set<String> current = savedTripCardIdsLiveData.getValue();
        Set<String> updated = current == null ? new HashSet<>() : new HashSet<>(current);
        updated.add(normalized);
        savedTripCardIdsLiveData.postValue(updated);
    }

    private String resolveOverrideTargetTripId(String payloadCurrentTripId) {
        String payloadTripId = trim(payloadCurrentTripId);
        if (!payloadTripId.isEmpty() && hasTripIdInCurrentTrips(payloadTripId)) {
            return payloadTripId;
        }
        return getCurrentTripId();
    }

    public void addSuggestedPlaceToTrip(String tripId, Place place) {
        if (tripId == null || tripId.trim().isEmpty() || place == null) {
            return;
        }

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

    public void refreshMessages() {
        if (groupId != null && !groupId.trim().isEmpty()) {
            chatRepository.refreshMessages(groupId);
            tripRepository.refreshTrips(groupId);
        }
    }

    private void sendDraftTripQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        TripPlan currentTrip = resolveCurrentGroupTrip();
        String aiQuery = buildDraftQueryWithTripDates(query.trim(), currentTrip);
        String progressMessageId = UUID.randomUUID().toString();
        long progressSentAt = System.currentTimeMillis();
        upsertAiStatusMessage(progressMessageId, progressSentAt, AI_DRAFTING_MESSAGE);

        LiveData<AiTripDraftResult> source = chatRepository.draftTripFromQuery(aiQuery);
        observeOnce(source, result -> {
            String failureCode = result != null ? result.getFailureCode() : "AI_FAILURE";
            if (failureCode != null) {
                aiDraftModeEnabledLiveData.postValue(false);
                upsertAiStatusMessage(
                        progressMessageId,
                        progressSentAt,
                        AI_DRAFT_ERROR_PREFIX + failureCode);
                return;
            }

            ChatMessage draftCard = buildDraftCardMessage(result);
            if (draftCard == null) {
                aiDraftModeEnabledLiveData.postValue(false);
                upsertAiStatusMessage(
                        progressMessageId,
                        progressSentAt,
                        AI_DRAFT_ERROR_PREFIX + "INVALID_DRAFT");
                return;
            }
            upsertAiStatusMessage(progressMessageId, progressSentAt, AI_DRAFTED_MESSAGE);
            chatRepository.sendMessage(draftCard);
        });
    }

    private void sendSuggestedPlacesQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        String progressMessageId = UUID.randomUUID().toString();
        long progressSentAt = System.currentTimeMillis();
        upsertAiStatusMessage(progressMessageId, progressSentAt, AI_SUGGESTING_MESSAGE);

        LiveData<AiPlaceSuggestionResult> source = placeRepository.suggestPlacesFromQuery(query);
        observeOnce(source, result -> {
            String failureCode = result != null ? result.getFailureCode() : "AI_FAILURE";
            if (failureCode != null) {
                aiSuggestPlacesModeEnabledLiveData.postValue(false);
                upsertAiStatusMessage(
                        progressMessageId,
                        progressSentAt,
                        AI_SUGGEST_ERROR_PREFIX + failureCode);
                return;
            }

            ChatMessage suggestedPlacesCard = buildSuggestedPlacesCardMessage(result);
            if (suggestedPlacesCard == null) {
                aiSuggestPlacesModeEnabledLiveData.postValue(false);
                upsertAiStatusMessage(
                        progressMessageId,
                        progressSentAt,
                        AI_SUGGEST_ERROR_PREFIX + "INVALID_RESULT");
                return;
            }
            upsertAiStatusMessage(progressMessageId, progressSentAt, AI_SUGGESTED_MESSAGE);
            chatRepository.sendMessage(suggestedPlacesCard);
        });
    }

    private void upsertAiStatusMessage(String messageId, long sentAt, String content) {
        chatRepository.insertLocalMessage(new ChatMessage(
                messageId,
                groupId,
                AI_SENDER_USER_ID,
                AI_SENDER_NAME,
                content,
                "TEXT",
                sentAt,
                UUID.randomUUID().toString(),
                0,
                0,
                null,
                true,
                false
        ));
    }

    private ChatMessage buildDraftCardMessage(AiTripDraftResult result) {
        if (result == null || result.getDraft() == null) {
            return null;
        }

        AiTripDraft draft = result.getDraft();
        List<AiTripDraftStop> stops = draft.getStops();
        int stopCount = stops != null ? stops.size() : 0;
        TripPlan currentTrip = resolveCurrentGroupTrip();

        String draftTripId = "ai-draft-" + UUID.randomUUID();
        String payload = buildDraftPayloadJson(
                draftTripId,
                draft,
                stops,
                result.getCandidatePlaces(),
                stopCount,
                currentTrip
        );

        return new ChatMessage(
                UUID.randomUUID().toString(),
                groupId,
                trim(currentUserId),
                null,
                payload,
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

    private ChatMessage buildSuggestedPlacesCardMessage(AiPlaceSuggestionResult result) {
        if (result == null || result.getPlaces() == null || result.getPlaces().isEmpty()) {
            return null;
        }

        List<Place> places = new ArrayList<>();
        for (AiPlaceSuggestion suggestion : result.getPlaces()) {
            if (suggestion == null || suggestion.getPlace() == null) {
                continue;
            }
            places.add(suggestion.getPlace());
        }
        if (places.isEmpty()) {
            return null;
        }

        String targetTripId = "";
        List<TripPlan> trips = tripsLiveData != null ? tripsLiveData.getValue() : null;
        if (trips != null && !trips.isEmpty() && trips.get(0) != null && trips.get(0).getId() != null) {
            targetTripId = trips.get(0).getId();
        }

        String payload = buildSuggestedPlacesPayloadJson(targetTripId, places);
        return new ChatMessage(
                UUID.randomUUID().toString(),
                groupId,
                trim(currentUserId),
                null,
                payload,
                "AI_SUGGESTED_PLACES_CARD",
                System.currentTimeMillis(),
                UUID.randomUUID().toString(),
                0,
                0,
                null,
                false,
                true,
                null,
                new ChatMessage.SuggestedPlacesCardData(targetTripId, places)
        );
    }

    private String buildDraftPayloadJson(String draftTripId,
            AiTripDraft draft,
            List<AiTripDraftStop> stops,
            List<Place> candidates,
            int stopCount,
            TripPlan currentTrip) {
        String currentTripId = currentTrip != null ? trim(currentTrip.getId()) : "";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"tripId\":").append(jsonString(draftTripId)).append(",");
        sb.append("\"currentTripId\":").append(jsonString(currentTripId)).append(",");
        sb.append("\"startAt\":").append(currentTrip != null ? currentTrip.getStartAt() : 0L).append(",");
        sb.append("\"endAt\":").append(currentTrip != null ? currentTrip.getEndAt() : 0L).append(",");
        sb.append("\"stopCount\":").append(stopCount).append(",");
        sb.append("\"isSaved\":false,");
        sb.append("\"totalDistance\":0.0,");
        sb.append("\"title\":").append(jsonString(draft.getTitle() != null ? draft.getTitle() : "AI Draft Trip")).append(",");
        String summary = draft.getSummary() != null ? draft.getSummary() : "";
        sb.append("\"summary\":").append(jsonString(summary)).append(",");
        sb.append("\"description\":").append(jsonString(summary)).append(",");
        sb.append("\"stops\":[");

        if (stops != null) {
            boolean firstStop = true;
            for (AiTripDraftStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                if (!firstStop) {
                    sb.append(",");
                }
                firstStop = false;

                Place place = stop.getPlace();
                Location location = place != null ? place.location : null;

                sb.append("{");
                sb.append("\"placeId\":").append(jsonString(stop.getPlaceId())).append(",");
                sb.append("\"durationMinutes\":").append(Math.max(0, stop.getDurationMinutes())).append(",");
                sb.append("\"startTime\":").append(jsonString(
                        stop.getStartTime() != null ? stop.getStartTime() : "")).append(",");
                sb.append("\"endTime\":").append(jsonString(
                        stop.getEndTime() != null ? stop.getEndTime() : "")).append(",");
                sb.append("\"note\":").append(jsonString(stop.getNote() != null ? stop.getNote() : "")).append(",");
                sb.append("\"plannedDateTime\":").append(jsonString(
                        stop.getPlannedDateTime() != null ? stop.getPlannedDateTime() : "")).append(",");
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
                if (place == null) {
                    continue;
                }
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

    private String buildSuggestedPlacesPayloadJson(String tripId, List<Place> places) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"tripId\":").append(jsonString(tripId == null ? "" : tripId)).append(",");
        sb.append("\"places\":[");
        boolean first = true;
        for (Place place : places) {
            if (place == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{");
            sb.append("\"id\":").append(jsonString(place.id)).append(",");
            sb.append("\"name\":").append(jsonString(place.name)).append(",");
            sb.append("\"address\":").append(jsonString(place.address)).append(",");
            sb.append("\"rating\":").append(place.rating).append(",");
            sb.append("\"latitude\":");
            appendNullableDouble(sb, place.location != null ? place.location.latitude : null);
            sb.append(",");
            sb.append("\"longitude\":");
            appendNullableDouble(sb, place.location != null ? place.location.longitude : null);
            sb.append("}");
        }
        sb.append("]}");
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

    private DraftTripCardSnapshot parseDraftTripPayload(String payloadJson, String fallbackDraftTripId) {
        String payload = trim(payloadJson);
        if (payload.isEmpty() || !payload.startsWith("{")) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(payload);
            String draftTripId = trim(json.optString("tripId", fallbackDraftTripId));
            String currentTripId = trim(json.optString("currentTripId", ""));
            String title = trim(json.optString("title", "AI Draft Trip"));
            if (title.isEmpty()) {
                title = "AI Draft Trip";
            }

            String description = trim(json.optString("summary", ""));
            if (description.isEmpty()) {
                description = trim(json.optString("description", ""));
            }

            long startAt = json.optLong("startAt", json.optLong("startTime", 0L));
            long endAt = json.optLong("endAt", json.optLong("endTime", 0L));
            List<TripStop> stops = toTripStops(json.optJSONArray("stops"), startAt, endAt);

            return new DraftTripCardSnapshot(
                    draftTripId,
                    currentTripId,
                    title,
                    description,
                    startAt,
                    endAt,
                    stops
            );
        } catch (Exception ignored) {
            return parseDraftTripPayloadFallback(payload, fallbackDraftTripId);
        }
    }

    private DraftTripCardSnapshot parseDraftTripPayloadFallback(String payload,
                                                                String fallbackDraftTripId) {
        String draftTripId = firstNonEmpty(
                extractQuotedValue(payload, "tripId"),
                trim(fallbackDraftTripId)
        );
        String currentTripId = extractQuotedValue(payload, "currentTripId");
        String title = firstNonEmpty(extractQuotedValue(payload, "title"), "AI Draft Trip");
        String description = firstNonEmpty(
                extractQuotedValue(payload, "summary"),
                extractQuotedValue(payload, "description")
        );
        long startAt = extractLongValue(payload, "startAt", extractLongValue(payload, "startTime", 0L));
        long endAt = extractLongValue(payload, "endAt", extractLongValue(payload, "endTime", 0L));
        List<TripStop> stops = parseStopsFromPayloadFallback(payload, startAt, endAt);

        return new DraftTripCardSnapshot(
                draftTripId,
                currentTripId,
                title,
                description,
                startAt,
                endAt,
                stops
        );
    }

    private List<TripStop> toTripStops(JSONArray stops, long fallbackStartAt, long fallbackEndAt) {
        List<TripStop> mapped = new ArrayList<>();
        if (stops == null) {
            return mapped;
        }

        AiDraftScheduleResolver.ScheduleCursor cursor =
                AiDraftScheduleResolver.newCursor(fallbackStartAt, fallbackEndAt);
        int orderIndex = 0;
        for (int i = 0; i < stops.length(); i++) {
            JSONObject stop = stops.optJSONObject(i);
            if (stop == null) {
                continue;
            }

            if (!stop.has("latitude") || !stop.has("longitude")) {
                continue;
            }
            double latitude = stop.optDouble("latitude", Double.NaN);
            double longitude = stop.optDouble("longitude", Double.NaN);
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                continue;
            }

            String stopTitle = trim(stop.optString("name", ""));
            String stopAddress = trim(stop.optString("address", ""));
            String note = trim(stop.optString("note", ""));
            int durationMinutes = Math.max(0, stop.optInt("durationMinutes", 0));
            AiDraftScheduleResolver.ScheduledTime scheduledTime =
                    AiDraftScheduleResolver.resolveStopTimes(
                            stop.optString("plannedDateTime", ""),
                            stop.optString("startTime", ""),
                            stop.optString("endTime", ""),
                            durationMinutes,
                            cursor
                    );
            long arrivalTime = scheduledTime.arrivalAt;
            long departureTime = scheduledTime.departureAt;

            mapped.add(new TripStop(
                    UUID.randomUUID().toString(),
                    stopTitle,
                    stopAddress,
                    note,
                    latitude,
                    longitude,
                    arrivalTime,
                    departureTime,
                    orderIndex
            ));
            orderIndex++;
        }
        return mapped;
    }

    private List<TripStop> parseStopsFromPayloadFallback(String payload,
                                                         long fallbackStartAt,
                                                         long fallbackEndAt) {
        List<TripStop> mapped = new ArrayList<>();
        String stopsSegment = extractStopsSegment(payload);
        if (stopsSegment.isEmpty()) {
            return mapped;
        }

        Matcher matcher = Pattern.compile("\\{([^{}]*)\\}").matcher(stopsSegment);
        AiDraftScheduleResolver.ScheduleCursor cursor =
                AiDraftScheduleResolver.newCursor(fallbackStartAt, fallbackEndAt);
        int orderIndex = 0;
        while (matcher.find()) {
            String stopJson = matcher.group(1);
            Double latitude = extractDoubleValue(stopJson, "latitude");
            Double longitude = extractDoubleValue(stopJson, "longitude");
            if (latitude == null || longitude == null
                    || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
                continue;
            }

            String name = extractQuotedValue(stopJson, "name");
            String address = extractQuotedValue(stopJson, "address");
            String note = extractQuotedValue(stopJson, "note");
            int durationMinutes = (int) extractLongValue(stopJson, "durationMinutes", 0L);
            AiDraftScheduleResolver.ScheduledTime scheduledTime =
                    AiDraftScheduleResolver.resolveStopTimes(
                            extractQuotedValue(stopJson, "plannedDateTime"),
                            extractQuotedValue(stopJson, "startTime"),
                            extractQuotedValue(stopJson, "endTime"),
                            durationMinutes,
                            cursor
                    );
            long arrivalTime = scheduledTime.arrivalAt;
            long departureTime = scheduledTime.departureAt;

            mapped.add(new TripStop(
                    UUID.randomUUID().toString(),
                    trim(name),
                    trim(address),
                    trim(note),
                    latitude,
                    longitude,
                    arrivalTime,
                    departureTime,
                    orderIndex
            ));
            orderIndex++;
        }
        return mapped;
    }

    private String extractStopsSegment(String payload) {
        int stopsKeyIndex = payload.indexOf("\"stops\"");
        if (stopsKeyIndex < 0) {
            return "";
        }
        int arrayStart = payload.indexOf('[', stopsKeyIndex);
        if (arrayStart < 0) {
            return "";
        }
        int depth = 0;
        for (int i = arrayStart; i < payload.length(); i++) {
            char ch = payload.charAt(i);
            if (ch == '[') {
                depth++;
            } else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    return payload.substring(arrayStart + 1, i);
                }
            }
        }
        return "";
    }

    private String extractQuotedValue(String payload, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\\\\\"])*)\"")
                .matcher(payload);
        if (!matcher.find()) {
            return "";
        }
        return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private long extractLongValue(String payload, String key, long defaultValue) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
                .matcher(payload);
        if (!matcher.find()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Double extractDoubleValue(String payload, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
                .matcher(payload);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group(1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstNonEmpty(String first, String fallback) {
        String firstTrimmed = trim(first);
        if (!firstTrimmed.isEmpty()) {
            return firstTrimmed;
        }
        return trim(fallback);
    }

    private long resolveStartAt(List<TripStop> stops) {
        return resolveStartAt(stops, 0L);
    }

    private long resolveStartAt(List<TripStop> stops, long fallbackStartAt) {
        long minArrival = Long.MAX_VALUE;
        if (stops != null) {
            for (TripStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                long arrival = stop.getArrivalTime();
                if (arrival > 0L && arrival < minArrival) {
                    minArrival = arrival;
                }
            }
        }
        if (minArrival == Long.MAX_VALUE) {
            return fallbackStartAt > 0L ? fallbackStartAt : System.currentTimeMillis();
        }
        return minArrival;
    }

    private long resolveEndAt(List<TripStop> stops, long startAt) {
        return resolveEndAt(stops, startAt, 0L);
    }

    private long resolveEndAt(List<TripStop> stops, long startAt, long fallbackEndAt) {
        long maxDeparture = 0L;
        if (stops != null) {
            for (TripStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                long departure = stop.getDepartureTime();
                if (departure > maxDeparture) {
                    maxDeparture = departure;
                }
            }
        }
        if (maxDeparture <= 0L) {
            return fallbackEndAt > 0L ? Math.max(startAt, fallbackEndAt) : startAt;
        }
        return Math.max(startAt, maxDeparture);
    }

    private void appendNullableDouble(StringBuilder sb, Double value) {
        if (value == null || !Double.isFinite(value)) {
            sb.append("null");
            return;
        }
        sb.append(value);
    }

    private TripPlan resolveCurrentGroupTrip() {
        List<TripPlan> trips = tripsLiveData != null ? tripsLiveData.getValue() : null;
        if (trips == null || trips.isEmpty()) {
            return null;
        }

        long now = System.currentTimeMillis();
        TripPlan activeTrip = null;
        TripPlan nearestUpcoming = null;
        long nearestUpcomingDelta = Long.MAX_VALUE;
        TripPlan latestPast = null;
        long latestPastEnd = Long.MIN_VALUE;

        for (TripPlan trip : trips) {
            if (trip == null || trim(trip.getId()).isEmpty()) {
                continue;
            }
            long startAt = trip.getStartAt();
            long endAt = trip.getEndAt();

            if (startAt > 0L && endAt > 0L && startAt <= now && endAt >= now) {
                activeTrip = trip;
                break;
            }

            if (startAt > now) {
                long delta = startAt - now;
                if (delta < nearestUpcomingDelta) {
                    nearestUpcomingDelta = delta;
                    nearestUpcoming = trip;
                }
            } else if (endAt > 0L && endAt <= now && endAt > latestPastEnd) {
                latestPastEnd = endAt;
                latestPast = trip;
            }
        }

        if (activeTrip != null) {
            return activeTrip;
        }
        if (nearestUpcoming != null) {
            return nearestUpcoming;
        }
        if (latestPast != null) {
            return latestPast;
        }
        return trips.get(0);
    }

    private String buildDraftQueryWithTripDates(String rawQuery, TripPlan currentTrip) {
        String query = trim(rawQuery);
        if (currentTrip == null) {
            return query;
        }
        return AiDraftPromptBuilder.buildDraftQueryWithDateRange(
                query,
                currentTrip.getStartAt(),
                currentTrip.getEndAt());
    }

    private boolean hasTripIdInCurrentTrips(String tripId) {
        String normalized = trim(tripId);
        if (normalized.isEmpty()) {
            return false;
        }
        List<TripPlan> trips = tripsLiveData != null ? tripsLiveData.getValue() : null;
        if (trips == null || trips.isEmpty()) {
            return false;
        }
        for (TripPlan trip : trips) {
            if (trip == null) {
                continue;
            }
            if (normalized.equals(trim(trip.getId()))) {
                return true;
            }
        }
        return false;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static class DraftTripCardSnapshot {

        final String draftTripId;
        final String currentTripId;
        final String title;
        final String description;
        final long startAt;
        final long endAt;
        final List<TripStop> stops;

        DraftTripCardSnapshot(String draftTripId,
                String currentTripId,
                String title,
                String description,
                long startAt,
                long endAt,
                List<TripStop> stops) {
            this.draftTripId = draftTripId;
            this.currentTripId = currentTripId;
            this.title = title;
            this.description = description;
            this.startAt = startAt;
            this.endAt = endAt;
            this.stops = stops;
        }
    }

    private <T> void observeOnce(LiveData<T> source, Observer<T> observer) {
        Observer<T> oneShotObserver = new Observer<>() {
            @Override
            public void onChanged(T value) {
                source.removeObserver(this);
                untrackObserveOnceObserver(source, this);
                observer.onChanged(value);
            }
        };

        trackObserveOnceObserver(source, oneShotObserver);
        source.observeForever(oneShotObserver);
    }

    private void trackObserveOnceObserver(LiveData<?> source, Observer<?> observer) {
        synchronized (observeOnceObservers) {
            Set<Observer<?>> observers = observeOnceObservers.computeIfAbsent(source, k -> new HashSet<>());
            observers.add(observer);
        }
    }

    private void untrackObserveOnceObserver(LiveData<?> source, Observer<?> observer) {
        synchronized (observeOnceObservers) {
            Set<Observer<?>> observers = observeOnceObservers.get(source);
            if (observers == null) {
                return;
            }
            observers.remove(observer);
            if (observers.isEmpty()) {
                observeOnceObservers.remove(source);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void clearObserveOnceObservers() {
        synchronized (observeOnceObservers) {
            for (Map.Entry<LiveData<?>, Set<Observer<?>>> entry : observeOnceObservers.entrySet()) {
                LiveData source = entry.getKey();
                for (Observer<?> observer : entry.getValue()) {
                    source.removeObserver(observer);
                }
            }
            observeOnceObservers.clear();
        }
    }

    @Override
    protected void onCleared() {
        clearObserveOnceObservers();
        super.onCleared();
        networkMonitor.observeConnectivity().removeObserver(networkObserver);
        chatRepository.disconnectFromGroup();
    }
}
