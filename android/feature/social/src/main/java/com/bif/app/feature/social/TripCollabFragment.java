package com.bif.app.feature.social;

import android.os.Bundle;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TripCollabFragment extends Fragment {

    private CollabAdapter adapter;

    public static TripCollabFragment newInstance(String tripId) {
        TripCollabFragment fragment = new TripCollabFragment();
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
        return inflater.inflate(R.layout.fragment_trip_collab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button btnAddCollaborator = view.findViewById(R.id.btn_add_collaborator);
        RecyclerView rvCollab = view.findViewById(R.id.rv_collab);
        TextView tvEmpty = view.findViewById(R.id.tv_collab_empty);

        adapter = new CollabAdapter();
        rvCollab.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCollab.setAdapter(adapter);

        btnAddCollaborator.setOnClickListener(v -> Toast.makeText(
                requireContext(),
                R.string.trip_feature_add_collab_soon,
                Toast.LENGTH_SHORT
        ).show());

        TripDetailViewModel viewModel = new ViewModelProvider(requireParentFragment())
                .get(TripDetailViewModel.class);

        if (viewModel.getTrip() != null) {
            viewModel.getTrip().observe(getViewLifecycleOwner(), trip -> bindTrip(trip, tvEmpty));
        }
    }

    private void bindTrip(@Nullable TripPlan trip, @NonNull TextView tvEmpty) {
        List<String> participants = trip == null || trip.getParticipantIds() == null
                ? Collections.emptyList()
                : new ArrayList<>(trip.getParticipantIds());

        adapter.setItems(participants);
        tvEmpty.setVisibility(participants.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static class CollabAdapter extends RecyclerView.Adapter<CollabAdapter.CollabViewHolder> {

        private final List<String> items = new ArrayList<>();

        @NonNull
        @Override
        public CollabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new CollabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CollabViewHolder holder, int position) {
            String memberId = items.get(position);
            holder.title.setText(memberId == null || memberId.trim().isEmpty()
                    ? holder.itemView.getContext().getString(R.string.trip_collab_member_unknown)
                    : memberId);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void setItems(@NonNull List<String> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        static class CollabViewHolder extends RecyclerView.ViewHolder {
            final TextView title;

            CollabViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
            }
        }
    }
}

