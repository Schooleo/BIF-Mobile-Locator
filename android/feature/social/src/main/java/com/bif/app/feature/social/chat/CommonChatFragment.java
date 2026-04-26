package com.bif.app.feature.social;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bif.app.core.utils.AppSnackbar;
import com.bif.app.core.utils.ChatReadStateStore;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.domain.model.ChatMessage;
import com.bif.app.domain.model.Location;
import com.bif.app.domain.model.Place;
import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class CommonChatFragment extends Fragment {

    private ChatMessageAdapter adapter;
    private ChatViewModel viewModel;
    private String chatType;
    private String chatId;
    private EditText messageInput;
    private RecyclerView rvMessages;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View layoutInputBar;
    private Drawable defaultInputBarBackground;
    private List<ChatMessage> latestMessages = new ArrayList<>();
    private int previousSoftInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED;
    private boolean applyingMention = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_common_chat, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        chatType = getArg(args, "chatType", "friend");
        chatId = getArg(args, "chatId", "");
        String chatName = getArg(args, "chatName", getString(R.string.chat_default_name));
        int memberCount = args != null ? args.getInt("memberCount", 0) : 0;
        long friendshipCreatedAt = args != null ? args.getLong("friendshipCreatedAt", 0L) : 0L;
        boolean supportsAiModes = "group".equalsIgnoreCase(chatType);

        TextView tvTitle = view.findViewById(R.id.tv_chat_title);
        TextView tvSubtitle = view.findViewById(R.id.tv_chat_subtitle);
        View btnBack = view.findViewById(R.id.btn_back);
        View btnHome = view.findViewById(R.id.btn_home);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_messages);
        rvMessages = view.findViewById(R.id.rv_messages);
        View composerBar = view.findViewById(R.id.layout_chat_composer);
        layoutInputBar = view.findViewById(R.id.layout_input_bar);
        defaultInputBarBackground = layoutInputBar.getBackground();
        View aiBadgesRow = view.findViewById(R.id.layout_ai_badges);
        MaterialCardView btnAiDraftTrip = view.findViewById(R.id.btn_ai_draft_trip);
        MaterialCardView btnAiSuggestPlaces = view.findViewById(R.id.btn_ai_suggest_places);
        EditText etMessage = view.findViewById(R.id.et_message);
        messageInput = etMessage;
        MaterialButton btnSend = view.findViewById(R.id.btn_send);
        applyKeyboardInsets(view, composerBar, rvMessages);

        tvTitle.setText(chatName);
        tvSubtitle.setText(resolveChatSubtitle(memberCount, friendshipCreatedAt));
        aiBadgesRow.setVisibility(supportsAiModes ? View.VISIBLE : View.GONE);
        btnHome.setVisibility(supportsAiModes ? View.VISIBLE : View.GONE);

        btnBack.setOnClickListener(v -> navigateBackFromChat(view));
        btnHome.setOnClickListener(v -> Navigation.findNavController(view)
            .navigate(UriUtils.buildUri(UriUtils.PathTo.SOCIAL)));
        view.setOnClickListener(v -> dismissKeyboardAndClearFocus());

        adapter = new ChatMessageAdapter(new ChatMessageAdapter.ChatActionCallback() {
            @Override
            public void onLocationLinkClick(ChatMessageAdapter.ChatMessage message) {
                handleLocationLinkClick(message);
            }

            @Override
            public void onSaveDraftAsNewTripClick(ChatMessageAdapter.TripCreatedCard card) {
                if (card == null || card.getTripId() == null || card.getTripId().trim().isEmpty()) {
                    return;
                }
                viewModel.onSaveTripCardAsNew(card.getTripId(), card.getPayloadJson());
            }

            @Override
            public void onOverrideCurrentTripClick(ChatMessageAdapter.TripCreatedCard card) {
                if (card == null || card.getTripId() == null || card.getTripId().trim().isEmpty()) {
                    return;
                }
                viewModel.onOverrideTripCard(card.getTripId(), card.getPayloadJson());
            }

            @Override
            public void onAddPlaceToTripClick(String tripId, ChatMessageAdapter.PlaceCard place) {
                String targetTripId = tripId == null ? "" : tripId.trim();
                if (targetTripId.isEmpty()) {
                    targetTripId = viewModel.getCurrentTripId();
                }
                if (targetTripId == null || targetTripId.trim().isEmpty()) {
                    AppSnackbar.show(requireContext(), R.string.chat_add_stop_no_trip);
                    return;
                }
                Place domainPlace = toDomainPlace(place);
                if (domainPlace == null) {
                    return;
                }
                viewModel.addSuggestedPlaceToTrip(targetTripId, domainPlace);
            }

            @Override
            public void onViewPlaceClick(ChatMessageAdapter.PlaceCard place) {
                handleViewPlaceClick(place);
            }
        });
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setAdapter(adapter);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshMessages();
            swipeRefreshLayout.postDelayed(() -> {
                if (isAdded()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 1000L);
        });

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        String currentUserId = UserPreferences.getId(requireContext());
        if (currentUserId.isEmpty()) {
            currentUserId = UserPreferences.getUsername(requireContext());
        }

        if (!chatId.isEmpty()) {
            viewModel.init(chatId, chatName, currentUserId);
            markGroupChatReadIfNeeded();
        }

        viewModel.getMessages().observe(getViewLifecycleOwner(), this::onMessagesUpdated);
        viewModel.getSavedTripCardIds().observe(getViewLifecycleOwner(), savedIds -> {
            if (!latestMessages.isEmpty()) {
                updateSavedStateForMessages();
            }
        });
        viewModel.getTrips().observe(getViewLifecycleOwner(), trips -> {
            if (!latestMessages.isEmpty()) {
                updateSavedStateForMessages();
            }
        });
        viewModel.getAiBadgesEnabled().observe(getViewLifecycleOwner(), enabled -> {
            boolean isEnabled = supportsAiModes && Boolean.TRUE.equals(enabled) && viewModel.isAuthenticated();
            aiBadgesRow.setVisibility(supportsAiModes ? View.VISIBLE : View.GONE);
            btnAiDraftTrip.setClickable(true);
            btnAiSuggestPlaces.setClickable(true);
            float alpha = isEnabled ? 1f : 0.45f;
            btnAiDraftTrip.setAlpha(alpha);
            btnAiSuggestPlaces.setAlpha(alpha);
            if (!isEnabled) {
                viewModel.cancelAiDraftMode();
                viewModel.cancelAiSuggestPlacesMode();
            }
        });
        viewModel.getAiDraftModeEnabled().observe(getViewLifecycleOwner(), isDraftMode -> {
            boolean enabled = Boolean.TRUE.equals(isDraftMode);
            boolean suggestEnabled = Boolean.TRUE.equals(viewModel.getAiSuggestPlacesModeEnabled().getValue());
            applyAiComposeMode(enabled, suggestEnabled, etMessage);
            applyAiBadgeSelection(btnAiDraftTrip, enabled);
            applyAiBadgeSelection(btnAiSuggestPlaces, suggestEnabled);
        });
        viewModel.getAiSuggestPlacesModeEnabled().observe(getViewLifecycleOwner(), isSuggestMode -> {
            boolean draftEnabled = Boolean.TRUE.equals(viewModel.getAiDraftModeEnabled().getValue());
            boolean suggestEnabled = Boolean.TRUE.equals(isSuggestMode);
            applyAiComposeMode(draftEnabled, suggestEnabled, etMessage);
            applyAiBadgeSelection(btnAiDraftTrip, draftEnabled);
            applyAiBadgeSelection(btnAiSuggestPlaces, suggestEnabled);
        });
        viewModel.getSnackbarMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) {
                return;
            }
            AppSnackbar.showLong(requireContext(), message);
            viewModel.clearSnackbarMessage();
        });

        if (savedInstanceState == null) {
            appendSharedPlaceMessageIfPresent(args);
        }

        rvMessages.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                dismissKeyboardAndClearFocus();
            }
            return false;
        });
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState != RecyclerView.SCROLL_STATE_IDLE) {
                    dismissKeyboardAndClearFocus();
                }
            }
        });

        btnSend.setOnClickListener(v -> {
            String input = etMessage.getText().toString().trim();
            if (input.isEmpty()) return;
            viewModel.sendMessage(input);
            etMessage.setText("");
            focusInputAndShowKeyboard(etMessage);
        });

        btnAiDraftTrip.setOnClickListener(v -> {
            if (!viewModel.isAuthenticated()) {
                AppSnackbar.show(requireContext(), R.string.social_login_required_ai);
                return;
            }
            if (!viewModel.isAiAvailable()) {
                AppSnackbar.show(requireContext(), R.string.chat_ai_offline);
                return;
            }
            viewModel.enterAiDraftMode();
            focusInputAndShowKeyboard(etMessage);
        });
        btnAiSuggestPlaces.setOnClickListener(v -> {
            if (!viewModel.isAuthenticated()) {
                AppSnackbar.show(requireContext(), R.string.social_login_required_ai);
                return;
            }
            if (!viewModel.isAiAvailable()) {
                AppSnackbar.show(requireContext(), R.string.chat_ai_offline);
                return;
            }
            viewModel.enterAiSuggestPlacesMode();
            focusInputAndShowKeyboard(etMessage);
        });

        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (applyingMention) {
                    return;
                }
                maybeShowMentionPopup(etMessage, s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        previousSoftInputMode = requireActivity().getWindow().getAttributes().softInputMode;
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        if (messageInput != null) {
            messageInput.clearFocus();
        }
    }

    @Override
    public void onPause() {
        requireActivity().getWindow().setSoftInputMode(previousSoftInputMode);
        super.onPause();
    }

    // ─── LiveData observers ────────────────────────────────────────────────────

    private void onMessagesUpdated(List<ChatMessage> messages) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        renderMessages(messages, true);
    }

    private void renderMessages(List<ChatMessage> messages, boolean withSideEffects) {
        if (messages == null) return;
        latestMessages = new ArrayList<>(messages);
        List<ChatMessageAdapter.ChatMessage> adapterMessages = new ArrayList<>();
        for (ChatMessage msg : messages) {
            adapterMessages.add(domainToAdapterMessage(msg));
        }
        adapter.submit(adapterMessages);
        if (withSideEffects) {
            markGroupChatReadIfNeeded();
            scrollToBottom();
        }
    }

    private void updateSavedStateForMessages() {
        List<ChatMessageAdapter.ChatMessage> adapterMessages = new ArrayList<>();
        for (ChatMessage msg : latestMessages) {
            adapterMessages.add(domainToAdapterMessage(msg));
        }
        adapter.submit(adapterMessages);
    }

    private ChatMessageAdapter.ChatMessage domainToAdapterMessage(ChatMessage msg) {
        String senderDisplay = msg.getSenderName() != null && !msg.getSenderName().isEmpty()
                ? msg.getSenderName()
                : (msg.getSenderUserId() != null ? msg.getSenderUserId() : "");
        if (msg.isOutgoing()) senderDisplay = getString(R.string.chat_you);

        String time = buildMessageStatusLabel(msg);

        if (msg.getMessageType() == ChatMessage.MessageType.TRIP_CREATED_CARD) {
            return buildTripCreatedCardMessage(msg, senderDisplay, time);
        }
        if (msg.getMessageType() == ChatMessage.MessageType.AI_SUGGESTED_PLACES_CARD) {
            return buildSuggestedPlacesCardMessage(msg, senderDisplay, time);
        }

        if (msg.isLocationMessage()) {
            String title = msg.getContent() != null && !msg.getContent().isEmpty()
                    ? msg.getContent()
                    : msg.getSharedAddress();
            return new ChatMessageAdapter.ChatMessage(
                    senderDisplay,
                    title != null ? title : "",
                    msg.getSharedAddress() != null ? msg.getSharedAddress() : "",
                    getString(R.string.chat_seed_group_5),
                    buildMapQuery(msg),
                    time,
                    msg.isOutgoing(),
                    ChatMessageAdapter.MessageType.LOCATION
            );
        }

        return new ChatMessageAdapter.ChatMessage(
                senderDisplay,
                msg.getContent() != null ? msg.getContent() : "",
                "",
                "",
                time,
                msg.isOutgoing(),
                ChatMessageAdapter.MessageType.TEXT
        );
    }

    private ChatMessageAdapter.ChatMessage buildTripCreatedCardMessage(ChatMessage msg,
                                                                       String senderDisplay,
                                                                       String time) {
        ChatMessageAdapter.TripCreatedCard card = parseTripCreatedCard(msg);
        return ChatMessageAdapter.ChatMessage.tripCreatedCard(
                senderDisplay,
                time,
                msg.isOutgoing(),
                card
        );
    }

    private ChatMessageAdapter.ChatMessage buildSuggestedPlacesCardMessage(ChatMessage msg,
                                                                           String senderDisplay,
                                                                           String time) {
        ChatMessageAdapter.SuggestedPlacesCard card = parseSuggestedPlacesCard(msg);
        return ChatMessageAdapter.ChatMessage.suggestedPlacesCard(
                senderDisplay,
                time,
                msg.isOutgoing(),
                card
        );
    }

    private ChatMessageAdapter.TripCreatedCard parseTripCreatedCard(ChatMessage msg) {
        ChatMessage.TripCreatedCardData data = msg.getTripCreatedCardData();
        String tripId = "";
        String tripTitle = "";
        String tripDescription = "";
        int stopCount = 0;
        double totalDistance = 0d;
        boolean isSaved = false;
        JSONArray parsedStops = null;
        List<SocialViewModel.AiDraftStopPreview> stopPreviews = Collections.emptyList();
        String payloadJson = msg.getContent() != null ? msg.getContent() : "";

        if (data != null) {
            tripId = data.getTripId() != null ? data.getTripId() : "";
            stopCount = Math.max(0, data.getStopCount());
            totalDistance = Math.max(0d, data.getTotalDistance());
            isSaved = data.isSaved();
        }

        if (payloadJson.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(payloadJson);
                tripId = json.optString("tripId", tripId);
                stopCount = json.optInt("stopCount", stopCount);
                isSaved = json.optBoolean("isSaved", isSaved);
                tripTitle = json.optString("title", tripTitle);
                tripDescription = json.optString("summary", tripDescription);
                if (tripDescription == null || tripDescription.trim().isEmpty()) {
                    tripDescription = json.optString("description", tripDescription);
                }
                parsedStops = json.optJSONArray("stops");
                stopPreviews = mapStopPreviews(parsedStops);
                if (stopCount <= 0) {
                    stopCount = stopPreviews.size();
                }

                double distanceFromPayload = json.optDouble("totalDistance", -1d);
                if (distanceFromPayload >= 0d) {
                    totalDistance = distanceFromPayload;
                }

                if (totalDistance <= 0d && parsedStops != null) {
                    JSONArray stopsWithCoordinates = filterStopsWithValidCoordinates(parsedStops);
                    if (stopsWithCoordinates.length() >= 2) {
                        totalDistance = computeManhattanDistance(stopsWithCoordinates);
                    }
                }
            } catch (JSONException ignored) {
                // Fallback to defaults for malformed payloads.
            }
        }

        if (tripTitle == null || tripTitle.trim().isEmpty()) {
            tripTitle = getString(R.string.trip_ai_title_fallback);
        }
        if (tripDescription == null || tripDescription.trim().isEmpty()) {
            tripDescription = getString(R.string.trip_overview_no_description);
        }
        String totalDistanceLabel = formatDistanceLabel(totalDistance);
        boolean alreadyHandled = isSaved || viewModel.isTripCardSaved(tripId);
        boolean hasTripId = tripId != null && !tripId.trim().isEmpty();
        boolean isHost = viewModel.isCurrentUserHostForCurrentTrip();
        boolean canSaveAsNew = hasTripId && isHost && !alreadyHandled;
        boolean canOverrideCurrent = canSaveAsNew && viewModel.hasCurrentTripForOverride();
        boolean showHostOnlyHint = hasTripId && !isHost && !alreadyHandled;

        return new ChatMessageAdapter.TripCreatedCard(
                tripId,
                tripTitle,
                tripDescription,
                Math.max(stopCount, 0),
                totalDistanceLabel,
                stopPreviews,
                payloadJson,
                canSaveAsNew,
                canOverrideCurrent,
                showHostOnlyHint
        );
    }

    private ChatMessageAdapter.SuggestedPlacesCard parseSuggestedPlacesCard(ChatMessage msg) {
        String tripId = "";
        List<ChatMessageAdapter.PlaceCard> places = new ArrayList<>();

        ChatMessage.SuggestedPlacesCardData data = msg.getSuggestedPlacesCardData();
        if (data != null) {
            tripId = data.getTripId() != null ? data.getTripId() : "";
            for (Place place : data.getPlaces()) {
                if (place == null) continue;
                places.add(new ChatMessageAdapter.PlaceCard(
                        place.id,
                        place.name,
                        place.address,
                        place.rating,
                        place.location != null ? place.location.latitude : Double.NaN,
                        place.location != null ? place.location.longitude : Double.NaN
                ));
            }
        }

        String content = msg.getContent();
        if (content != null && content.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(content);
                tripId = json.optString("tripId", tripId);

                JSONArray items = json.optJSONArray("places");
                if (items != null) {
                    places.clear();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject placeJson = items.optJSONObject(i);
                        if (placeJson == null) continue;
                        double latitude = placeJson.has("latitude")
                                ? placeJson.optDouble("latitude", Double.NaN)
                                : Double.NaN;
                        double longitude = placeJson.has("longitude")
                                ? placeJson.optDouble("longitude", Double.NaN)
                                : Double.NaN;
                        places.add(new ChatMessageAdapter.PlaceCard(
                                placeJson.optString("id", ""),
                                placeJson.optString("name", "Unknown place"),
                                placeJson.optString("address", ""),
                                placeJson.optDouble("rating", 0d),
                                latitude,
                                longitude
                        ));
                    }
                }
            } catch (JSONException ignored) {
                // Fallback to defaults for malformed payloads.
            }
        }

        String targetTripId = resolveSuggestedPlacesTargetTripId(tripId);
        Set<String> existingStopKeys = buildExistingStopKeys(targetTripId);
        List<ChatMessageAdapter.PlaceCard> resolvedPlaces = new ArrayList<>(places.size());
        for (ChatMessageAdapter.PlaceCard place : places) {
            if (place == null) {
                continue;
            }
            boolean isAdded = isPlaceAlreadyInTrip(place, existingStopKeys);
            resolvedPlaces.add(new ChatMessageAdapter.PlaceCard(
                    place.getId(),
                    place.getName(),
                    place.getAddress(),
                    place.getRating(),
                    place.getLatitude(),
                    place.getLongitude(),
                    isAdded
            ));
        }

        return new ChatMessageAdapter.SuggestedPlacesCard(targetTripId, resolvedPlaces);
    }

    private String resolveSuggestedPlacesTargetTripId(@Nullable String payloadTripId) {
        String resolved = payloadTripId == null ? "" : payloadTripId.trim();
        if (!resolved.isEmpty()) {
            return resolved;
        }
        String currentTripId = viewModel != null ? viewModel.getCurrentTripId() : "";
        return currentTripId == null ? "" : currentTripId.trim();
    }

    private Set<String> buildExistingStopKeys(@Nullable String tripId) {
        Set<String> keys = new HashSet<>();
        if (tripId == null || tripId.trim().isEmpty() || viewModel == null) {
            return keys;
        }

        List<TripPlan> trips = viewModel.getTrips().getValue();
        if (trips == null || trips.isEmpty()) {
            return keys;
        }

        for (TripPlan trip : trips) {
            if (trip == null || trip.getId() == null || !tripId.equals(trip.getId())) {
                continue;
            }

            List<TripStop> stops = trip.getStops();
            if (stops == null || stops.isEmpty()) {
                break;
            }

            for (TripStop stop : stops) {
                if (stop == null) {
                    continue;
                }
                if (Double.isFinite(stop.getLatitude()) && Double.isFinite(stop.getLongitude())) {
                    keys.add(buildCoordinateKey(stop.getLatitude(), stop.getLongitude()));
                }
                keys.add(buildIdentityKey(stop.getTitle(), stop.getAddress()));
            }
            break;
        }
        return keys;
    }

    private boolean isPlaceAlreadyInTrip(@NonNull ChatMessageAdapter.PlaceCard place,
                                         @NonNull Set<String> existingStopKeys) {
        if (existingStopKeys.isEmpty()) {
            return false;
        }
        if (place.hasCoordinates()
                && existingStopKeys.contains(buildCoordinateKey(place.getLatitude(), place.getLongitude()))) {
            return true;
        }
        return existingStopKeys.contains(buildIdentityKey(place.getName(), place.getAddress()));
    }

    private String buildCoordinateKey(double latitude, double longitude) {
        return String.format(Locale.US, "%.6f,%.6f", latitude, longitude);
    }

    private String buildIdentityKey(@Nullable String name, @Nullable String address) {
        String normalizedName = name == null ? "" : name.trim().toLowerCase(Locale.US);
        String normalizedAddress = address == null ? "" : address.trim().toLowerCase(Locale.US);
        return normalizedName + "|" + normalizedAddress;
    }

    @NonNull
    private JSONArray filterStopsWithValidCoordinates(@Nullable JSONArray stops) {
        JSONArray filtered = new JSONArray();
        if (stops == null) {
            return filtered;
        }

        for (int i = 0; i < stops.length(); i++) {
            JSONObject stop = stops.optJSONObject(i);
            if (stop == null) {
                continue;
            }

            Double latitude = optNullableDouble(stop, "latitude");
            Double longitude = optNullableDouble(stop, "longitude");
            if (latitude == null || longitude == null) {
                continue;
            }
            if (latitude < -90d || latitude > 90d || longitude < -180d || longitude > 180d) {
                continue;
            }
            if (Double.compare(latitude, 0.0d) == 0 && Double.compare(longitude, 0.0d) == 0) {
                continue;
            }

            filtered.put(stop);
        }

        return filtered;
    }

    private double computeManhattanDistance(@Nullable JSONArray stops) {
        if (stops == null || stops.length() < 2) return 0d;

        double total = 0d;
        double prevLat = 0d;
        double prevLng = 0d;
        boolean hasPrev = false;

        for (int i = 0; i < stops.length(); i++) {
            JSONObject stop = stops.optJSONObject(i);
            if (stop == null) continue;

            double lat = stop.optDouble("latitude", 0d);
            double lng = stop.optDouble("longitude", 0d);
            if (hasPrev) {
                total += haversineDistanceKm(prevLat, prevLng, lat, lng);
            }
            prevLat = lat;
            prevLng = lng;
            hasPrev = true;
        }
        return total;
    }

    private double haversineDistanceKm(double fromLat, double fromLng, double toLat, double toLng) {
        final double earthRadiusKm = 6371d;
        double dLat = Math.toRadians(toLat - fromLat);
        double dLng = Math.toRadians(toLng - fromLng);
        double lat1 = Math.toRadians(fromLat);
        double lat2 = Math.toRadians(toLat);

        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2d) * Math.sin(dLng / 2d);
        double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return earthRadiusKm * c;
    }

    private String formatDistanceLabel(double distance) {
        if (distance <= 0d) {
            return "0.0";
        }
        return String.format(Locale.US, "%.1f", distance);
    }

    private List<SocialViewModel.AiDraftStopPreview> mapStopPreviews(@Nullable JSONArray stops) {
        if (stops == null || stops.length() == 0) {
            return Collections.emptyList();
        }

        List<SocialViewModel.AiDraftStopPreview> previews = new ArrayList<>();
        for (int i = 0; i < stops.length(); i++) {
            JSONObject stop = stops.optJSONObject(i);
            if (stop == null) {
                continue;
            }

            String placeId = stop.optString("placeId", stop.optString("id", ""));

            String name = stop.optString("name", "").trim();
            if (name.isEmpty()) {
                name = getString(R.string.trip_stop_untitled);
            }

            String address = stop.optString("address", "").trim();
            if (address.isEmpty()) {
                address = getString(R.string.trip_stop_no_address);
            }

            String note = stop.optString("note", "").trim();
            String plannedDateTime = stop.optString("plannedDateTime", "").trim();
            String startTime = stop.optString("startTime", "").trim();
            String endTime = stop.optString("endTime", "").trim();
            int durationMinutes = Math.max(0, stop.optInt("duration", stop.optInt("durationMinutes", 0)));
            Double latitude = optNullableDouble(stop, "latitude");
            Double longitude = optNullableDouble(stop, "longitude");

            previews.add(new SocialViewModel.AiDraftStopPreview(
                    placeId,
                    name,
                    address,
                    note,
                    plannedDateTime,
                    startTime,
                    endTime,
                    durationMinutes,
                    latitude,
                    longitude
            ));
        }

        return previews;
    }

    @Nullable
    private Double optNullableDouble(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        double value = json.optDouble(key, Double.NaN);
        return Double.isFinite(value) ? value : null;
    }

    private String buildMessageStatusLabel(ChatMessage msg) {
        if (msg.isOutgoing() && !msg.isConfirmed()) {
            return getString(R.string.chat_status_sending);
        }
        return DateFormat.format("HH:mm", new Date(msg.getSentAt())).toString();
    }

    private void applyAiComposeMode(boolean draftEnabled,
                                    boolean suggestEnabled,
                                    EditText etMessage) {
        if (layoutInputBar == null) {
            return;
        }
        if (draftEnabled) {
            layoutInputBar.setBackgroundResource(R.drawable.bg_chat_input_ai_draft);
            etMessage.setHint(R.string.chat_ai_draft_hint);
        } else if (suggestEnabled) {
            layoutInputBar.setBackgroundResource(R.drawable.bg_chat_input_ai_draft);
            etMessage.setHint(R.string.chat_ai_suggest_hint);
        } else {
            if (defaultInputBarBackground != null) {
                layoutInputBar.setBackground(defaultInputBarBackground);
            }
            etMessage.setHint(R.string.chat_hint);
        }
    }

    private void applyAiBadgeSelection(@NonNull MaterialCardView badge,
                                       boolean selected) {
        badge.setCardElevation(selected ? 6f : 2f);
        badge.setStrokeWidth(selected ? 2 : 1);
        badge.setStrokeColor(selected
                ? resolveThemeColorByName("colorPrimary",
                ContextCompat.getColor(requireContext(), com.bif.app.core.R.color.primary_green))
                : resolveThemeColorByName("colorSurface",
                android.graphics.Color.TRANSPARENT));
        badge.setCardBackgroundColor(resolveThemeColorByName(
                selected ? "colorListItemBackground" : "colorSurfaceBox",
                android.graphics.Color.TRANSPARENT));
    }

    private int resolveThemeColorByName(@NonNull String attrName, int fallbackColor) {
        int attrResId = requireContext().getResources().getIdentifier(
                attrName,
                "attr",
                requireContext().getPackageName());
        if (attrResId == 0) {
            attrResId = requireContext().getResources().getIdentifier(attrName, "attr", "android");
        }
        if (attrResId == 0) {
            return fallbackColor;
        }

        android.util.TypedValue typedValue = new android.util.TypedValue();
        if (requireContext().getTheme().resolveAttribute(attrResId, typedValue, true)) {
            return typedValue.data;
        }
        return fallbackColor;
    }

    private String resolveChatSubtitle(int memberCountArg, long friendshipCreatedAt) {
        if ("group".equalsIgnoreCase(chatType)) {
            return getString(R.string.chat_member_count, Math.max(memberCountArg, 1));
        }
        if (friendshipCreatedAt > 0L) {
            CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                    friendshipCreatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS);
            return getString(R.string.chat_friend_added_relative, relativeTime);
        }
        return getString(R.string.chat_direct_subtitle);
    }

    private void maybeShowMentionPopup(EditText input, String fullText) {
        int cursor = input.getSelectionStart();
        if (cursor < 0 || cursor > fullText.length()) {
            return;
        }

        int tokenStart = cursor - 1;
        while (tokenStart >= 0 && !Character.isWhitespace(fullText.charAt(tokenStart))) {
            tokenStart--;
        }
        tokenStart++;

        if (tokenStart >= fullText.length() || fullText.charAt(tokenStart) != '@') {
            return;
        }

        String token = fullText.substring(tokenStart, cursor).toLowerCase(Locale.US);
        List<String> suggestions = buildMentionSuggestions(token);
        if (suggestions.isEmpty()) {
            return;
        }

        PopupMenu popupMenu = new PopupMenu(requireContext(), input);
        for (int i = 0; i < suggestions.size(); i++) {
            popupMenu.getMenu().add(0, i, i, suggestions.get(i));
        }
        int start = tokenStart;
        int end = cursor;
        popupMenu.setOnMenuItemClickListener(item -> {
            String mention = suggestions.get(item.getItemId());
            String updated = fullText.substring(0, start) + mention + " " + fullText.substring(end);
            applyingMention = true;
            input.setText(updated);
            int nextCursor = Math.min(updated.length(), start + mention.length() + 1);
            input.setSelection(nextCursor);
            applyingMention = false;
            return true;
        });
        popupMenu.show();
    }

    private List<String> buildMentionSuggestions(String token) {
        List<String> candidates = new ArrayList<>();
        candidates.add("@AI Trip Drafter");

        for (ChatMessage message : latestMessages) {
            if (message == null) {
                continue;
            }
            String name = message.getSenderName();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            String mention = "@" + name.trim();
            if (!candidates.contains(mention)) {
                candidates.add(mention);
            }
        }

        if (token.length() <= 1) {
            return candidates;
        }

        List<String> filtered = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.US).startsWith(token)) {
                filtered.add(candidate);
            }
        }
        return filtered;
    }

    private String buildMapQuery(ChatMessage msg) {
        if (msg.getSharedLatitude() != 0 || msg.getSharedLongitude() != 0) {
            return msg.getSharedLatitude() + "," + msg.getSharedLongitude();
        }
        return msg.getSharedAddress() != null ? msg.getSharedAddress() : "";
    }

    private Place toDomainPlace(ChatMessageAdapter.PlaceCard placeCard) {
        if (placeCard == null || !placeCard.hasCoordinates()) {
            return null;
        }
        return new Place(
                placeCard.getId(),
                placeCard.getName(),
                placeCard.getAddress(),
                placeCard.getRating(),
                new Location(placeCard.getLatitude(), placeCard.getLongitude())
        );
    }

    private void appendSharedPlaceMessageIfPresent(Bundle args) {
        if (args == null || !"group".equalsIgnoreCase(chatType)) return;

        String placeName = getArg(args, "sharedPlaceName", "");
        if (placeName.isEmpty()) return;

        double lat = 0, lng = 0;
        String address = getArg(args, "sharedPlaceAddress", "");
        try {
            lat = Double.parseDouble(getArg(args, "sharedPlaceLat", "0"));
            lng = Double.parseDouble(getArg(args, "sharedPlaceLng", "0"));
        } catch (NumberFormatException ignored) {}

        viewModel.shareLocation(lat, lng, placeName);
    }

    private void handleLocationLinkClick(ChatMessageAdapter.ChatMessage message) {
        String mapQuery = message.getMapQuery();
        if (mapQuery == null || mapQuery.trim().isEmpty()) {
            mapQuery = message.getSubtitle() != null && !message.getSubtitle().trim().isEmpty()
                    ? message.getSubtitle() : message.getTitle();
        }
        android.net.Uri mapUri = UriUtils.buildUri("/map")
                .buildUpon()
                .appendQueryParameter("location", mapQuery)
                .build();
        Navigation.findNavController(requireView()).navigate(mapUri);
    }

    private void handleViewPlaceClick(ChatMessageAdapter.PlaceCard place) {
        if (place == null) {
            return;
        }

        String location;
        if (place.hasCoordinates()) {
            location = place.getLatitude() + "," + place.getLongitude();
        } else {
            location = place.getAddress() != null && !place.getAddress().trim().isEmpty()
                    ? place.getAddress()
                    : place.getName();
        }

        android.net.Uri.Builder mapUriBuilder = UriUtils.buildUri("/map")
                .buildUpon()
                .appendQueryParameter("location", location)
                .appendQueryParameter("focusName", place.getName() != null ? place.getName() : "")
                .appendQueryParameter("focusAddress", place.getAddress() != null ? place.getAddress() : "")
                .appendQueryParameter("focusPlaceId", place.getId() != null ? place.getId() : "")
                .appendQueryParameter("focusRating", String.valueOf(place.getRating()));

        android.net.Uri mapUri = mapUriBuilder.build();
        Navigation.findNavController(requireView()).navigate(mapUri);
    }

    private void scrollToBottom() {
        if (rvMessages != null && adapter.getItemCount() > 0) {
            rvMessages.post(() -> rvMessages.scrollToPosition(adapter.getItemCount() - 1));
        }
    }

    private String getArg(Bundle args, String key, String fallback) {
        if (args == null) return fallback;
        String value = args.getString(key);
        return (value == null || value.trim().isEmpty()) ? fallback : value;
    }

    private void navigateBackFromChat(View rootView) {
        if ("group".equalsIgnoreCase(chatType)) {
            getParentFragmentManager().setFragmentResult("groupDetailResult", new Bundle());
        }

        NavController navController = Navigation.findNavController(rootView);
        if (navController.popBackStack()) {
            return;
        }

        android.net.Uri socialUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL);
        navController.navigate(socialUri);
    }

    private void markGroupChatReadIfNeeded() {
        if (!"group".equalsIgnoreCase(chatType) || chatId == null || chatId.trim().isEmpty()) {
            return;
        }
        ChatReadStateStore.markGroupReadNow(requireContext(), chatId);
    }

    private void dismissKeyboardAndClearFocus() {
        if (messageInput == null || !isAdded()) {
            return;
        }

        messageInput.clearFocus();
        View rootView = getView();
        if (rootView != null) {
            rootView.requestFocus();
        }

        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(messageInput);
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.ime());
        }

        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.hideSoftInputFromWindow(messageInput.getWindowToken(), 0);
        }
    }

    private void focusInputAndShowKeyboard(EditText etMessage) {
        etMessage.requestFocus();
        etMessage.setFocusableInTouchMode(true);

        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(etMessage);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.ime());
        }

        InputMethodManager imm = requireContext().getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
        }

        etMessage.postDelayed(() -> {
            if (isAdded()) {
                if (!etMessage.hasFocus()) etMessage.requestFocus();
                WindowInsetsControllerCompat delayedController = ViewCompat.getWindowInsetsController(etMessage);
                if (delayedController != null) {
                    delayedController.show(WindowInsetsCompat.Type.ime());
                }
                if (imm != null) {
                    imm.showSoftInput(etMessage, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 120);
    }

    private void applyKeyboardInsets(View root, View inputBar, RecyclerView messagesView) {
        final int inputPadLeft = inputBar.getPaddingLeft();
        final int inputPadTop = inputBar.getPaddingTop();
        final int inputPadRight = inputBar.getPaddingRight();
        final int inputPadBottom = inputBar.getPaddingBottom();

        final int listPadLeft = messagesView.getPaddingLeft();
        final int listPadTop = messagesView.getPaddingTop();
        final int listPadRight = messagesView.getPaddingRight();
        final int listPadBottom = messagesView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int imeOffset = Math.max(0, imeInsets.bottom - systemInsets.bottom);
            inputBar.setTranslationY(-imeOffset);

            int inputBarHeight = inputBar.getHeight();
            int recyclerBottomPadding = listPadBottom + inputBarHeight + imeOffset;
            messagesView.setPadding(listPadLeft, listPadTop, listPadRight, recyclerBottomPadding);
            inputBar.setPadding(inputPadLeft, inputPadTop, inputPadRight, inputPadBottom);
            return insets;
        });

        inputBar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                ViewCompat.requestApplyInsets(root)
        );
        ViewCompat.requestApplyInsets(root);
    }
}
