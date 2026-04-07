package com.bif.app.feature.social;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface ChatActionCallback {
        void onLocationLinkClick(ChatMessage message);
        void onSaveTripClick(String tripId);
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
                    "Trip created",
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
                    "AI suggested places",
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
        private final int stopCount;
        private final String startTime;
        private final String totalDistance;
        private final boolean saved;

        public TripCreatedCard(String tripId,
                               int stopCount,
                               String startTime,
                               String totalDistance,
                               boolean saved) {
            this.tripId = tripId;
            this.stopCount = stopCount;
            this.startTime = startTime;
            this.totalDistance = totalDistance;
            this.saved = saved;
        }

        public String getTripId() {
            return tripId;
        }

        public int getStopCount() {
            return stopCount;
        }

        public String getStartTime() {
            return startTime;
        }

        public String getTotalDistance() {
            return totalDistance;
        }

        public boolean isSaved() {
            return saved;
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

        public PlaceCard(String id,
                         String name,
                         String address,
                         double rating,
                         double latitude,
                         double longitude) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.rating = rating;
            this.latitude = latitude;
            this.longitude = longitude;
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

    @SuppressLint("NotifyDataSetChanged")
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
        private final TextView tvStopCount;
        private final TextView tvStartTime;
        private final TextView tvTotalDistance;
        private final TextView tvTime;
        private final Button btnSaveTrip;
        private final ChatActionCallback actionCallback;

        TripCreatedMessageViewHolder(@NonNull View itemView, ChatActionCallback actionCallback) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvTitle = itemView.findViewById(R.id.tv_trip_created_title);
            tvStopCount = itemView.findViewById(R.id.tv_trip_stop_count_value);
            tvStartTime = itemView.findViewById(R.id.tv_trip_start_time_value);
            tvTotalDistance = itemView.findViewById(R.id.tv_trip_total_distance_value);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            btnSaveTrip = itemView.findViewById(R.id.btn_save_trip);
            this.actionCallback = actionCallback;
        }

        void bind(ChatMessage message) {
            if (message.isMine()) {
                tvSender.setVisibility(View.GONE);
            } else {
                tvSender.setVisibility(View.VISIBLE);
                tvSender.setText(message.getSender());
            }

            tvTitle.setText(message.getTitle());
            tvTime.setText(message.getTime());

            TripCreatedCard card = message.getTripCreatedCard();
            if (card == null) {
                tvStopCount.setText("-");
                tvStartTime.setText("-");
                tvTotalDistance.setText("-");
                btnSaveTrip.setVisibility(View.GONE);
                btnSaveTrip.setOnClickListener(null);
                return;
            }

            tvStopCount.setText(String.valueOf(card.getStopCount()));
            tvStartTime.setText(card.getStartTime());
            tvTotalDistance.setText(card.getTotalDistance());

            if (card.isSaved()) {
                btnSaveTrip.setVisibility(View.GONE);
                btnSaveTrip.setOnClickListener(null);
            } else {
                btnSaveTrip.setVisibility(View.VISIBLE);
                btnSaveTrip.setOnClickListener(v -> {
                    if (actionCallback != null && card.getTripId() != null && !card.getTripId().trim().isEmpty()) {
                        actionCallback.onSaveTripClick(card.getTripId());
                    }
                });
            }
        }
    }

    static class SuggestedPlacesMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSender;
        private final TextView tvTitle;
        private final TextView tvTime;
        private final SuggestedPlaceCardAdapter nestedAdapter;

        SuggestedPlacesMessageViewHolder(@NonNull View itemView, ChatActionCallback actionCallback) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvTitle = itemView.findViewById(R.id.tv_suggested_places_title);
            tvTime = itemView.findViewById(R.id.tv_message_time);
            RecyclerView rvSuggestedPlaces = itemView.findViewById(R.id.rv_suggested_places);

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

            tvTitle.setText(message.getTitle());
            tvTime.setText(message.getTime());

            SuggestedPlacesCard card = message.getSuggestedPlacesCard();
            if (card == null) {
                nestedAdapter.submit(Collections.emptyList(), "");
                return;
            }
            nestedAdapter.submit(card.getPlaces(), card.getTripId());
        }
    }

    static class SuggestedPlaceCardAdapter extends RecyclerView.Adapter<SuggestedPlaceCardAdapter.PlaceCardViewHolder> {
        private final List<PlaceCard> places = new ArrayList<>();
        private final ChatActionCallback actionCallback;
        private String tripId = "";

        SuggestedPlaceCardAdapter(ChatActionCallback actionCallback) {
            this.actionCallback = actionCallback;
        }

        @SuppressLint("NotifyDataSetChanged")
        void submit(List<PlaceCard> newPlaces, String tripId) {
            places.clear();
            if (newPlaces != null) {
                places.addAll(newPlaces);
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
            holder.bind(places.get(position), tripId);
        }

        @Override
        public int getItemCount() {
            return places.size();
        }

        static class PlaceCardViewHolder extends RecyclerView.ViewHolder {
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

            void bind(PlaceCard place, String tripId) {
                tvName.setText(place.getName());
                tvAddress.setText(place.getAddress());
                if (place.getRating() > 0d) {
                    tvRating.setVisibility(View.VISIBLE);
                    tvRating.setText(String.format(Locale.US, "Rating %.1f", place.getRating()));
                } else {
                    tvRating.setVisibility(View.GONE);
                }

                btnAddToTrip.setOnClickListener(v -> {
                    if (actionCallback != null) {
                        actionCallback.onAddPlaceToTripClick(tripId, place);
                    }
                });

                btnViewPlace.setOnClickListener(v -> {
                    if (actionCallback != null) {
                        actionCallback.onViewPlaceClick(place);
                    }
                });
            }
        }
    }
}
