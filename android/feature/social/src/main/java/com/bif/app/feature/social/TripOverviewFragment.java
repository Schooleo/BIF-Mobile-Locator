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
        TextView tvStats = view.findViewById(R.id.tv_overview_stats);
        TextView tvDates = view.findViewById(R.id.tv_overview_dates);
        TextView tvDescription = view.findViewById(R.id.tv_overview_description);

        TripDetailViewModel viewModel = new ViewModelProvider(requireParentFragment())
                .get(TripDetailViewModel.class);

        if (viewModel.getTrip() != null) {
            viewModel.getTrip().observe(getViewLifecycleOwner(), trip -> bindTrip(trip, tvTitle, tvStats, tvDates, tvDescription));
        }
    }

    private void bindTrip(TripPlan trip,
                          TextView tvTitle,
                          TextView tvStats,
                          TextView tvDates,
                          TextView tvDescription) {
        if (trip == null) {
            return;
        }

        tvTitle.setText(trip.getTitle());
        int travelers = trip.getParticipantIds() == null ? 0 : trip.getParticipantIds().size();
        tvStats.setText(getString(R.string.stops_count) + ": " + trip.getStopCount() + "  •  "
                + getString(R.string.travelers_count) + ": " + travelers);

        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        String start = trip.getStartAt() > 0 ? formatter.format(new Date(trip.getStartAt())) : "-";
        String end = trip.getEndAt() > 0 ? formatter.format(new Date(trip.getEndAt())) : "-";
        tvDates.setText(start + " - " + end);

        tvDescription.setText(trip.getDescription() == null ? "" : trip.getDescription());
    }
}

