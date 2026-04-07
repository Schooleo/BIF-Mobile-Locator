package com.bif.app.feature.social;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.TripPlan;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ACTION = 0;
    private static final int VIEW_TYPE_TRIP = 1;

    private final OnTripActionListener listener;
    private final List<TripPlan> trips = new ArrayList<>();

    public interface OnTripActionListener {
        void onCreateTripClick();
        void onTripClick(TripPlan trip);
        void onTripMoreClick(TripPlan trip, View anchorView);
    }

    public TripListAdapter(OnTripActionListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setTrips(List<TripPlan> items) {
        trips.clear();
        if (items != null) {
            trips.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_ACTION : VIEW_TYPE_TRIP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ACTION) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(com.bif.app.core.R.layout.component_action_list_item, parent, false);
            return new ActionViewHolder(view);
        }

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_trip_card, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ActionViewHolder) {
            ((ActionViewHolder) holder).bind();
            return;
        }

        TripPlan trip = trips.get(position - 1);
        ((TripViewHolder) holder).bind(trip);
    }

    @Override
    public int getItemCount() {
        return trips.size() + 1;
    }

    private String formatRange(long startAt, long endAt) {
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String start = startAt > 0 ? formatter.format(new Date(startAt)) : "-";
        String end = endAt > 0 ? formatter.format(new Date(endAt)) : "-";
        return start + " - " + end;
    }

    class ActionViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvActionText;
        private final android.widget.ImageView ivActionIcon;

        ActionViewHolder(View itemView) {
            super(itemView);
            tvActionText = itemView.findViewById(com.bif.app.core.R.id.tv_action_text);
            ivActionIcon = itemView.findViewById(com.bif.app.core.R.id.iv_action_icon);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCreateTripClick();
                }
            });
        }

        void bind() {
            tvActionText.setText(R.string.plan_new_trip);
            ivActionIcon.setImageResource(com.bif.app.core.R.drawable.ic_trip);
        }
    }

    class TripViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTripTitle;
        private final TextView tvDateRange;
        private final TextView tvMembersCount;
        private final ImageButton btnMore;

        TripViewHolder(View itemView) {
            super(itemView);
            tvTripTitle = itemView.findViewById(R.id.tv_trip_title);
            tvDateRange = itemView.findViewById(R.id.tv_date_range);
            tvMembersCount = itemView.findViewById(R.id.tv_members_count);
            btnMore = itemView.findViewById(R.id.btn_more);
        }

        void bind(TripPlan trip) {
            String title = trip.getTitle() == null || trip.getTitle().trim().isEmpty()
                    ? "Untitled Trip"
                    : trip.getTitle().trim();

            tvTripTitle.setText(title);
            int travelerCount = trip.getParticipantIds() == null ? 0 : trip.getParticipantIds().size();
            tvDateRange.setText(formatRange(trip.getStartAt(), trip.getEndAt()));
                tvMembersCount.setText(itemView.getResources().getQuantityString(
                    R.plurals.trip_travelers_count,
                    travelerCount,
                    travelerCount
                ));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTripClick(trip);
                }
            });

            btnMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTripMoreClick(trip, v);
                }
            });
        }
    }
}

