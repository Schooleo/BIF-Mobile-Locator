package com.bif.app.feature.social;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
    }

    public TripListAdapter(OnTripActionListener listener) {
        this.listener = listener;
    }

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

    private int[] colorsFromTitle(String title) {
        String safeTitle = title == null ? "trip" : title;
        int hash = Math.abs(safeTitle.hashCode());
        int[][] palette = new int[][] {
                {Color.parseColor("#56CCF2"), Color.parseColor("#2F80ED")},
                {Color.parseColor("#11998E"), Color.parseColor("#38EF7D")},
                {Color.parseColor("#FF5F6D"), Color.parseColor("#FFC371")},
                {Color.parseColor("#FC466B"), Color.parseColor("#3F5EFB")},
                {Color.parseColor("#00B09B"), Color.parseColor("#96C93D")}
        };
        return palette[hash % palette.length];
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
        private final View banner;
        private final TextView tvTripTitle;
        private final TextView tvStopCount;
        private final TextView tvTravelerCount;
        private final TextView tvDateRange;
        private final TextView tvDescription;
        private final ImageButton btnMore;

        TripViewHolder(View itemView) {
            super(itemView);
            banner = itemView.findViewById(R.id.view_trip_banner);
            tvTripTitle = itemView.findViewById(R.id.tv_trip_title);
            tvStopCount = itemView.findViewById(R.id.tv_stop_count);
            tvTravelerCount = itemView.findViewById(R.id.tv_traveler_count);
            tvDateRange = itemView.findViewById(R.id.tv_date_range);
            tvDescription = itemView.findViewById(R.id.tv_description);
            btnMore = itemView.findViewById(R.id.btn_more);
        }

        void bind(TripPlan trip) {
            String title = trip.getTitle() == null || trip.getTitle().trim().isEmpty()
                    ? "Untitled Trip"
                    : trip.getTitle().trim();

            int[] colors = colorsFromTitle(title);
            GradientDrawable gradient = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    colors
            );
            banner.setBackground(gradient);

            tvTripTitle.setText(title);
            tvStopCount.setText(String.valueOf(trip.getStopCount()));
            int travelerCount = trip.getParticipantIds() == null ? 0 : trip.getParticipantIds().size();
            tvTravelerCount.setText(String.valueOf(travelerCount));
            tvDateRange.setText(formatRange(trip.getStartAt(), trip.getEndAt()));
            tvDescription.setText(trip.getDescription() == null ? "" : trip.getDescription());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTripClick(trip);
                }
            });

            btnMore.setOnClickListener(v -> {
                // Placeholder for future menu actions.
            });
        }
    }
}

