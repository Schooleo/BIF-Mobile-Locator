package com.bif.app.feature.social;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.TripPlan;
import com.bif.app.domain.model.TripStop;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TripItineraryFragment extends Fragment {

    private ItineraryAdapter adapter;

    public static TripItineraryFragment newInstance(String tripId) {
        TripItineraryFragment fragment = new TripItineraryFragment();
        Bundle args = new Bundle();
        args.putString("tripId", tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trip_itinerary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnAddStop = view.findViewById(R.id.btn_add_stop);
        RecyclerView rvItinerary = view.findViewById(R.id.rv_itinerary);
        TextView tvEmpty = view.findViewById(R.id.tv_itinerary_empty);

        adapter = new ItineraryAdapter();
        rvItinerary.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvItinerary.setAdapter(adapter);

        btnAddStop.setOnClickListener(v -> Toast.makeText(
                requireContext(),
                R.string.trip_feature_add_stop_soon,
                Toast.LENGTH_SHORT
        ).show());

        TripDetailViewModel viewModel = new ViewModelProvider(requireParentFragment())
                .get(TripDetailViewModel.class);

        if (viewModel.getTrip() != null) {
            viewModel.getTrip().observe(getViewLifecycleOwner(), trip -> bindTrip(trip, tvEmpty));
        }
    }

    private void bindTrip(@Nullable TripPlan trip, @NonNull TextView tvEmpty) {
        List<TripStop> stops = trip == null || trip.getStops() == null
                ? Collections.emptyList()
                : new ArrayList<>(trip.getStops());

        stops.sort(Comparator.comparingInt(TripStop::getOrderIndex));
        adapter.setItems(stops);
        tvEmpty.setVisibility(stops.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static class ItineraryAdapter extends RecyclerView.Adapter<ItineraryAdapter.StopViewHolder> {

        private final List<TripStop> items = new ArrayList<>();
        private final SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public StopViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new StopViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull StopViewHolder holder, int position) {
            TripStop stop = items.get(position);

            String title = stop.getTitle();
            if (title == null || title.trim().isEmpty()) {
                title = holder.itemView.getContext().getString(R.string.trip_stop_untitled);
            }
            holder.title.setText((position + 1) + ". " + title);

            String note = stop.getNote();
            if (TextUtils.isEmpty(note)) {
                note = holder.itemView.getContext().getString(R.string.trip_stop_no_note);
            }

            String timePart = "";
            if (stop.getArrivalTime() > 0) {
                timePart = formatter.format(new Date(stop.getArrivalTime()));
            } else if (stop.getDepartureTime() > 0) {
                timePart = formatter.format(new Date(stop.getDepartureTime()));
            }

            if (timePart.isEmpty()) {
                holder.subtitle.setText(note);
            } else {
                holder.subtitle.setText(note + "  •  " + timePart);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void setItems(@NonNull List<TripStop> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        static class StopViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView subtitle;

            StopViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}

