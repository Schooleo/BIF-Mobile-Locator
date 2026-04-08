package com.bif.app.feature.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bif.app.domain.model.TripPlan;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TripOverviewFragment extends Fragment {

    public static TripOverviewFragment newInstance(String tripId) {
        TripOverviewFragment fragment = new TripOverviewFragment();
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
        return inflater.inflate(R.layout.fragment_trip_overview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tv_overview_title);
        TextView tvStops = view.findViewById(R.id.tv_overview_stops);
        TextView tvTravelers = view.findViewById(R.id.tv_overview_travelers);
        TextView tvDates = view.findViewById(R.id.tv_overview_dates);
        TextView tvDescription = view.findViewById(R.id.tv_overview_description);

        TripDetailViewModel viewModel = new ViewModelProvider(requireParentFragment())
                .get(TripDetailViewModel.class);

        if (viewModel.getTrip() != null) {
            viewModel.getTrip().observe(getViewLifecycleOwner(), trip -> bindTrip(
                trip,
                tvTitle,
                tvStops,
                tvTravelers,
                tvDates,
                tvDescription
            ));
        }
    }

    private void bindTrip(TripPlan trip,
                          TextView tvTitle,
                  TextView tvStops,
                  TextView tvTravelers,
                          TextView tvDates,
                          TextView tvDescription) {
        if (trip == null) {
            return;
        }

        String title = trip.getTitle() == null || trip.getTitle().trim().isEmpty()
            ? getString(R.string.trip_title_hint)
            : trip.getTitle().trim();
        tvTitle.setText(title);

        tvStops.setText(String.valueOf(trip.getStopCount()));
        int travelers = trip.getParticipantIds() == null ? 0 : trip.getParticipantIds().size();
        tvTravelers.setText(String.valueOf(travelers));

        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String start = trip.getStartAt() > 0 ? formatter.format(new Date(trip.getStartAt())) : "-";
        String end = trip.getEndAt() > 0 ? formatter.format(new Date(trip.getEndAt())) : "-";
        tvDates.setText(start + " - " + end);

        String description = trip.getDescription() == null || trip.getDescription().trim().isEmpty()
            ? getString(R.string.trip_overview_no_description)
            : trip.getDescription().trim();
        tvDescription.setText(description);
    }
}

