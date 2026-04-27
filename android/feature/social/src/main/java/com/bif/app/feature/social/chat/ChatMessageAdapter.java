package com.bif.app.feature.social.chat;

import com.bif.app.feature.social.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.feature.social.ai.AiTripDraftStopPreviewAdapter;
import com.bif.app.feature.social.core.SocialViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface ChatActionCallback {

        void onLocationLinkClick(ChatMessage message);

        void onSaveDraftAsNewTripClick(TripCreatedCard card);

        void onOverrideCurrentTripClick(TripCreatedCard card);

        void onAddPlaceToTripClick(String tripId, PlaceCard place);

        void onViewPlaceClick(PlaceCard place);
    }

    public enum MessageType {
        TEXT,
        LOCATION,
        EVENT,
        TRIP_CREATED_CARD,
        AI_SUGGESTED_PLACES_CARD
    }

    public static class ChatMessage {

        private final String sender;
        private final String title;
        private final String subtitle;
        private final String linkText;
        private final String mapQuery;
        private final String time;
        private final boolean mine;
        private final MessageType type;
        private final TripCreatedCard tripCreatedCard;
        private final SuggestedPlacesCard suggestedPlacesCard;

        public ChatMessage(String sender, String title, String subtitle, String linkText, String time, boolean mine, MessageType type) {
            this(sender, title, subtitle, linkText, "", time, mine, type, null, null);
        }

        public ChatMessage(String sender,
                String title,
                String subtitle,
                String linkText,
                String mapQuery,
                String time,
                boolean mine,
                MessageType type) {
            this(sender, title, subtitle, linkText, mapQuery, time, mine, type, null, null);
        }

        public ChatMessage(String sender,
                String title,
                String subtitle,
                String linkText,
                String mapQuery,
                String time,
                boolean mine,
                MessageType type,
                TripCreatedCard tripCreatedCard,
                SuggestedPlacesCard suggestedPlacesCard) {
            this.sender = sender;
            this.title = title;
            this.subtitle = subtitle;
            this.linkText = linkText;
            this.mapQuery = mapQuery;
            this.time = time;
            this.mine = mine;
            this.type = type;
            this.tripCreatedCard = tripCreatedCard;
            this.suggestedPlacesCard = suggestedPlacesCard;
        }

        public static ChatMessage tripCreatedCard(String sender,
                String time,
                boolean mine,
                TripCreatedCard card) {
            return new ChatMessage(
                    sender,
                    "",
                    "",
                    "",
                    "",
                    time,
                    mine,
                    MessageType.TRIP_CREATED_CARD,
                    card,
                    null
            );
        }

        public static ChatMessage suggestedPlacesCard(String sender,
                String time,
                boolean mine,
                SuggestedPlacesCard card) {
            return new ChatMessage(
                    sender,
                    "",
                    "",
                    "",
                    "",
                    time,
                    mine,
                    MessageType.AI_SUGGESTED_PLACES_CARD,
                    null,
                    card
            );
        }

        public String getSender() {
            return sender;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public String getLinkText() {
            return linkText;
        }

        public String getTime() {
            return time;
        }

        public String getMapQuery() {
            return mapQuery;
        }

        public boolean isMine() {
            return mine;
        }

        public MessageType getType() {
            return type;
        }

        public TripCreatedCard getTripCreatedCard() {
            return tripCreatedCard;
        }

        public SuggestedPlacesCard getSuggestedPlacesCard() {
            return suggestedPlacesCard;
        }
    }

    public static class TripCreatedCard {

        private final String tripId;
        private final String title;
        private final String description;
        private final int stopCount;
        private final String totalDistance;
        private final List<SocialViewModel.AiDraftStopPreview> stopPreviews;
        private final String payloadJson;
        private final boolean canSaveAsNew;
        private final boolean canOverrideCurrent;
        private final boolean showHostActionHint;

        public TripCreatedCard(String tripId,
                String title,
                String description,
                int stopCount,
                String totalDistance,
                List<SocialViewModel.AiDraftStopPreview> stopPreviews,
                String payloadJson,
                boolean canSaveAsNew,
                boolean canOverrideCurrent,
                boolean showHostActionHint) {
            this.tripId = tripId;
            this.title = title;
            this.description = description;
            this.stopCount = stopCount;
            this.totalDistance = totalDistance;
            this.stopPreviews = stopPreviews == null
                    ? Collections.emptyList()
                    : new ArrayList<>(stopPreviews);
            this.payloadJson = payloadJson;
            this.canSaveAsNew = canSaveAsNew;
            this.canOverrideCurrent = canOverrideCurrent;
            this.showHostActionHint = showHostActionHint;
        }

        public String getTripId() {
            return tripId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public int getStopCount() {
            return stopCount;
        }

        public String getTotalDistance() {
            return totalDistance;
        }

        public List<SocialViewModel.AiDraftStopPreview> getStopPreviews() {
            return new ArrayList<>(stopPreviews);
        }

        public String getPayloadJson() {
            return payloadJson;
        }

        public boolean canSaveAsNew() {
            return canSaveAsNew;
        }

        public boolean canOverrideCurrent() {
            return canOverrideCurrent;
        }

        public boolean shouldShowHostActionHint() {
            return showHostActionHint;
        }
    }

    public static class SuggestedPlacesCard {

        private final String tripId;
        private final List<PlaceCard> places;

        public SuggestedPlacesCard(String tripId, List<PlaceCard> places) {
            this.tripId = tripId;
            this.places = places == null ? Collections.emptyList() : new ArrayList<>(places);
        }

        public String getTripId() {
            return tripId;
        }

        public List<PlaceCard> getPlaces() {
            return new ArrayList<>(places);
        }
    }

    public static class PlaceCard {

        private final String id;
        private final String name;
        private final String address;
        private final double rating;
        private final double latitude;
        private final double longitude;
        private final boolean added;

        public PlaceCard(String id,
                String name,
                String address,
                double rating,
                double latitude,
                double longitude) {
            this(id, name, address, rating, latitude, longitude, false);
        }

        public PlaceCard(String id,
                String name,
                String address,
                double rating,
                double latitude,
                double longitude,
                boolean added) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.rating = rating;
            this.latitude = latitude;
            this.longitude = longitude;
            this.added = added;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public double getRating() {
            return rating;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public boolean hasCoordinates() {
            return Double.isFinite(latitude) && Double.isFinite(longitude);
        }

        public boolean isAdded() {
            return added;
        }
    }

    private static final int VIEW_TYPE_TEXT_INCOMING = 1;
    private static final int VIEW_TYPE_TEXT_OUTGOING = 2;
    private static final int VIEW_TYPE_TRIP_CREATED_CARD = 3;
    private static final int VIEW_TYPE_SUGGESTED_PLACES_CARD = 4;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final ChatActionCallback actionCallback;

    public ChatMessageAdapter() {
        this(null);
    }

    public ChatMessageAdapter(ChatActionCallback actionCallback) {
        this.actionCallback = actionCallback;
    }

    public void submit(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        notifyDataSetChanged();
    }

    public void add(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        if (message.getType() == MessageType.TRIP_CREATED_CARD) {
            return VIEW_TYPE_TRIP_CREATED_CARD;
        }
        if (message.getType() == MessageType.AI_SUGGESTED_PLACES_CARD) {
            return VIEW_TYPE_SUGGESTED_PLACES_CARD;
        }
        return message.isMine() ? VIEW_TYPE_TEXT_OUTGOING : VIEW_TYPE_TEXT_INCOMING;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_TRIP_CREATED_CARD) {
            View view = inflater.inflate(R.layout.item_chat_trip_created, parent, false);
            return new TripCreatedMessageViewHolder(view, actionCallback);
        }
        if (viewType == VIEW_TYPE_SUGGESTED_PLACES_CARD) {
            View view = inflater.inflate(R.layout.item_chat_suggested_places, parent, false);
            return new SuggestedPlacesMessageViewHolder(view, actionCallback);
        }

        int layout = viewType == VIEW_TYPE_TEXT_OUTGOING
                ? R.layout.item_chat_outgoing
                : R.layout.item_chat_incoming;
        View view = inflater.inflate(layout, parent, false);
        return new TextMessageViewHolder(view, actionCallback);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof TripCreatedMessageViewHolder) {
            ((TripCreatedMessageViewHolder) holder).bind(message);
            return;
        }
        if (holder instanceof SuggestedPlacesMessageViewHolder) {
            ((SuggestedPlacesMessageViewHolder) holder).bind(message);
            return;
        }
        ((TextMessageViewHolder) holder).bind(message);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class TextMessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvSender;
        private final TextView tvTitle;
        private final TextView tvSubtitle;
        private final TextView tvLink;
        private final TextView tvTime;
        private final ChatActionCallback actionCallback;
        private final int defaultLinkColor;

        TextMessageViewHolder(@NonNull View itemView,
                ChatActionCallback actionCallback) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvTitle = itemView.findViewById(R.id.tv_message_title);
            tvSubtitle = itemView.findViewById(R.id.tv_message_subtitle);
            tvLink = itemView.findViewById(R.id.tv_message_link);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            this.actionCallback = actionCallback;
            this.defaultLinkColor = tvLink.getCurrentTextColor();
        }

        void bind(ChatMessage message) {
            if (message.isMine()) {
                tvSender.setVisibility(View.GONE);
            } else {
                tvSender.setVisibility(View.VISIBLE);
                tvSender.setText(message.getSender());
            }

            tvTitle.setText(message.getTitle());

            if (message.getSubtitle() == null || message.getSubtitle().isEmpty()) {
                tvSubtitle.setVisibility(View.GONE);
            } else {
                tvSubtitle.setVisibility(View.VISIBLE);
                tvSubtitle.setText(message.getSubtitle());
            }

            if (message.getLinkText() == null || message.getLinkText().isEmpty()) {
                tvLink.setVisibility(View.GONE);
                tvLink.setOnClickListener(null);
            } else {
                tvLink.setVisibility(View.VISIBLE);
                tvLink.setText(message.getLinkText());
                if (message.getType() == MessageType.LOCATION && actionCallback != null) {
                    tvLink.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.white));
                    tvLink.setOnClickListener(v -> actionCallback.onLocationLinkClick(message));
                } else {
                    tvLink.setTextColor(defaultLinkColor);
                    tvLink.setOnClickListener(null);
                }
            }

            tvTime.setText(message.getTime());
        }
    }

    static class TripCreatedMessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvSender;
        private final TextView tvTitle;
        private final TextView tvTripName;
        private final TextView tvTripDescription;
        private final TextView tvStopCount;
        private final TextView tvTotalDistance;
        private final RecyclerView rvStopPreview;
        private final AiTripDraftStopPreviewAdapter stopPreviewAdapter;
        private final TextView tvHostActionHint;
        private final TextView tvTime;
        private final Button btnSaveAsNewTrip;
        private final Button btnOverrideTrip;
        private final ChatActionCallback actionCallback;

        TripCreatedMessageViewHolder(@NonNull View itemView, ChatActionCallback actionCallback) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvTitle = itemView.findViewById(R.id.tv_trip_created_title);
            tvTripName = itemView.findViewById(R.id.tv_trip_draft_name);
            tvTripDescription = itemView.findViewById(R.id.tv_trip_draft_description);
            tvStopCount = itemView.findViewById(R.id.tv_trip_stop_count_value);
            tvTotalDistance = itemView.findViewById(R.id.tv_trip_total_distance_value);
            rvStopPreview = itemView.findViewById(R.id.rv_trip_stop_preview);
            rvStopPreview.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), RecyclerView.HORIZONTAL, false));
            stopPreviewAdapter = new AiTripDraftStopPreviewAdapter();
            rvStopPreview.setAdapter(stopPreviewAdapter);
            tvHostActionHint = itemView.findViewById(R.id.tv_host_action_hint);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            btnSaveAsNewTrip = itemView.findViewById(R.id.btn_save_new_trip);
            btnOverrideTrip = itemView.findViewById(R.id.btn_override_trip);
            this.actionCallback = actionCallback;
        }

        void bind(ChatMessage message) {
            if (message.isMine()) {
                tvSender.setVisibility(View.GONE);
            } else {
                tvSender.setVisibility(View.VISIBLE);
                tvSender.setText(message.getSender());
            }

            tvTitle.setText(itemView.getContext().getString(R.string.chat_trip_created_title));
            tvTime.setText(message.getTime());

            TripCreatedCard card = message.getTripCreatedCard();
            if (card == null) {
                tvStopCount.setText("-");
                tvTotalDistance.setText("-");
                tvTripName.setText("");
                tvTripDescription.setText("");
                stopPreviewAdapter.submit(Collections.emptyList());
                btnSaveAsNewTrip.setVisibility(View.GONE);
                btnSaveAsNewTrip.setOnClickListener(null);
                btnOverrideTrip.setVisibility(View.GONE);
                btnOverrideTrip.setOnClickListener(null);
                tvHostActionHint.setVisibility(View.GONE);
                resetStopPreviewScroll();
                return;
            }

            tvTripName.setText(card.getTitle());
            tvTripDescription.setText(card.getDescription());
            tvStopCount.setText(String.valueOf(card.getStopCount()));
            tvTotalDistance.setText(card.getTotalDistance());
            stopPreviewAdapter.submit(card.getStopPreviews());
            resetStopPreviewScroll();

            boolean hasTripId = card.getTripId() != null && !card.getTripId().trim().isEmpty();
            if (!hasTripId || !card.canSaveAsNew()) {
                btnSaveAsNewTrip.setVisibility(View.GONE);
                btnSaveAsNewTrip.setOnClickListener(null);
            } else {
                btnSaveAsNewTrip.setVisibility(View.VISIBLE);
                btnSaveAsNewTrip.setOnClickListener(v -> {
                    if (actionCallback != null) {
                        actionCallback.onSaveDraftAsNewTripClick(card);
                    }
                });
            }

            if (!hasTripId || !card.canOverrideCurrent()) {
                btnOverrideTrip.setVisibility(View.GONE);
                btnOverrideTrip.setOnClickListener(null);
            } else {
                btnOverrideTrip.setVisibility(View.VISIBLE);
                btnOverrideTrip.setOnClickListener(v -> {
                    if (actionCallback != null) {
                        actionCallback.onOverrideCurrentTripClick(card);
                    }
                });
            }

            tvHostActionHint.setVisibility(card.shouldShowHostActionHint() ? View.VISIBLE : View.GONE);
        }

        private void resetStopPreviewScroll() {
            RecyclerView.LayoutManager layoutManager = rvStopPreview.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(0, 0);
            } else {
                rvStopPreview.scrollToPosition(0);
            }
        }
    }

    static class SuggestedPlacesMessageViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvSender;
        private final TextView tvTitle;
        private final TextView tvTime;
        private final RecyclerView rvSuggestedPlaces;
        private final SuggestedPlaceCardAdapter nestedAdapter;

        SuggestedPlacesMessageViewHolder(@NonNull View itemView, ChatActionCallback actionCallback) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvTitle = itemView.findViewById(R.id.tv_suggested_places_title);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            rvSuggestedPlaces = itemView.findViewById(R.id.rv_suggested_places);

            rvSuggestedPlaces.setLayoutManager(
                    new LinearLayoutManager(itemView.getContext(), RecyclerView.HORIZONTAL, false));
            nestedAdapter = new SuggestedPlaceCardAdapter(actionCallback);
            rvSuggestedPlaces.setAdapter(nestedAdapter);
        }

        void bind(ChatMessage message) {
            if (message.isMine()) {
                tvSender.setVisibility(View.GONE);
            } else {
                tvSender.setVisibility(View.VISIBLE);
                tvSender.setText(message.getSender());
            }

            tvTitle.setText(itemView.getContext().getString(R.string.chat_suggested_places_title));
            tvTime.setText(message.getTime());

            SuggestedPlacesCard card = message.getSuggestedPlacesCard();
            if (card == null) {
                nestedAdapter.submit(Collections.emptyList(), "");
                resetSuggestedPlacesScroll();
                return;
            }
            nestedAdapter.submit(card.getPlaces(), card.getTripId());
            resetSuggestedPlacesScroll();
        }

        private void resetSuggestedPlacesScroll() {
            RecyclerView.LayoutManager layoutManager = rvSuggestedPlaces.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(0, 0);
            } else {
                rvSuggestedPlaces.scrollToPosition(0);
            }
        }
    }

    static class SuggestedPlaceCardAdapter extends RecyclerView.Adapter<SuggestedPlaceCardAdapter.PlaceCardViewHolder> {

        private final List<PlaceCard> places = new ArrayList<>();
        private final Set<String> addedPlaceKeys = new HashSet<>();
        private final ChatActionCallback actionCallback;
        private String tripId = "";

        SuggestedPlaceCardAdapter(ChatActionCallback actionCallback) {
            this.actionCallback = actionCallback;
        }

        void submit(List<PlaceCard> newPlaces, String tripId) {
            places.clear();
            if (newPlaces != null) {
                places.addAll(newPlaces);
            }
            addedPlaceKeys.clear();
            for (PlaceCard place : places) {
                if (place != null && place.isAdded()) {
                    addedPlaceKeys.add(buildPlaceKey(place));
                }
            }
            this.tripId = tripId == null ? "" : tripId;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public PlaceCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_suggested_place_card, parent, false);
            return new PlaceCardViewHolder(view, actionCallback);
        }

        @Override
        public void onBindViewHolder(@NonNull PlaceCardViewHolder holder, int position) {
            PlaceCard place = places.get(position);
            holder.bind(place, tripId, place.isAdded() || addedPlaceKeys.contains(buildPlaceKey(place)));
        }

        @Override
        public int getItemCount() {
            return places.size();
        }

        @NonNull
        private String buildPlaceKey(@NonNull PlaceCard place) {
            String id = place.getId();
            if (id != null && !id.trim().isEmpty()) {
                return "id:" + id.trim();
            }
            String name = place.getName() == null ? "" : place.getName().trim();
            String address = place.getAddress() == null ? "" : place.getAddress().trim();
            return "na:" + name + "|" + address;
        }

        class PlaceCardViewHolder extends RecyclerView.ViewHolder {

            private final TextView tvName;
            private final TextView tvAddress;
            private final TextView tvRating;
            private final Button btnAddToTrip;
            private final Button btnViewPlace;
            private final ChatActionCallback actionCallback;

            PlaceCardViewHolder(@NonNull View itemView, ChatActionCallback actionCallback) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tv_place_name);
                tvAddress = itemView.findViewById(R.id.tv_place_address);
                tvRating = itemView.findViewById(R.id.tv_place_rating);
                btnAddToTrip = itemView.findViewById(R.id.btn_add_to_trip);
                btnViewPlace = itemView.findViewById(R.id.btn_view_place);
                this.actionCallback = actionCallback;
            }

            void bind(PlaceCard place, String tripId, boolean added) {
                tvName.setText(place.getName());
                tvAddress.setText(place.getAddress());
                if (place.getRating() > 0d) {
                    tvRating.setVisibility(View.VISIBLE);
                    tvRating.setText(itemView.getContext().getString(
                            R.string.trip_stop_rating_format,
                            place.getRating()));
                } else {
                    tvRating.setVisibility(View.GONE);
                }

                if (place.hasCoordinates()) {
                    btnAddToTrip.setVisibility(View.VISIBLE);
                    btnAddToTrip.setText(added
                            ? itemView.getContext().getString(R.string.chat_added)
                            : itemView.getContext().getString(R.string.add_stop));
                    btnAddToTrip.setEnabled(!added);
                    btnAddToTrip.setOnClickListener(v -> {
                        if (added) {
                            return;
                        }
                        if (actionCallback != null) {
                            actionCallback.onAddPlaceToTripClick(tripId, place);
                            int adapterPosition = getBindingAdapterPosition();
                            if (adapterPosition != RecyclerView.NO_POSITION
                                    && adapterPosition < places.size()) {
                                addedPlaceKeys.add(buildPlaceKey(places.get(adapterPosition)));
                                notifyItemChanged(adapterPosition);
                            }
                        }
                    });
                } else {
                    btnAddToTrip.setVisibility(View.GONE);
                    btnAddToTrip.setOnClickListener(null);
                }

                btnViewPlace.setOnClickListener(v -> {
                    if (actionCallback != null) {
                        actionCallback.onViewPlaceClick(place);
                    }
                });
            }
        }
    }
}
