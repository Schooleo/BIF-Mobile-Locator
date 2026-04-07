package com.bif.app.feature.social;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.Place;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddTripStopAdapter extends RecyclerView.Adapter<AddTripStopAdapter.PlaceViewHolder> {

    interface OnPlaceClickListener {
        void onPlaceClick(AddTripStopViewModel.StopSearchResultItem item);
    }

    private final List<AddTripStopViewModel.StopSearchResultItem> items = new ArrayList<>();
    private final OnPlaceClickListener listener;

    AddTripStopAdapter(OnPlaceClickListener listener) {
        this.listener = listener;
    }

    void submitItems(@NonNull List<AddTripStopViewModel.StopSearchResultItem> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_add_trip_stop_result, parent, false);
        return new PlaceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaceViewHolder holder, int position) {
        AddTripStopViewModel.StopSearchResultItem item = items.get(position);
        Place place = item.place;

        String title = place != null && place.name != null && !place.name.trim().isEmpty()
                ? place.name
                : holder.itemView.getContext().getString(R.string.trip_stop_untitled);
        holder.tvPlaceName.setText(title);

        double rating = place != null ? place.rating : 0d;
        holder.tvPlaceRating.setText(String.format(Locale.getDefault(), "Rating %.1f", rating));
        holder.tvAddedCount.setText(holder.itemView.getContext().getString(
                R.string.trip_stop_added_count,
                item.addedToTripCount));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaceClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class PlaceViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPlaceName;
        final TextView tvPlaceRating;
        final TextView tvAddedCount;

        PlaceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPlaceName = itemView.findViewById(R.id.tv_place_name);
            tvPlaceRating = itemView.findViewById(R.id.tv_place_rating);
            tvAddedCount = itemView.findViewById(R.id.tv_place_added_count);
        }
    }
}
