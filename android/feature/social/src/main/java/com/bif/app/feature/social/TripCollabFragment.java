package com.bif.app.feature.social;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.domain.model.TripMember;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class TripCollabFragment extends Fragment {

    private static final String ARG_TRIP_ID = "tripId";

    private TripCollabViewModel viewModel;
    private CollabAdapter adapter;
    private String tripId = "";

    public static TripCollabFragment newInstance(String tripId) {
        TripCollabFragment fragment = new TripCollabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TRIP_ID, tripId);
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

        tripId = getArguments() != null ? getArguments().getString(ARG_TRIP_ID, "") : "";

        MaterialButton btnAddCollaborator = view.findViewById(R.id.btn_add_collaborator);
        RecyclerView rvCollab = view.findViewById(R.id.rv_collab);
        TextView tvEmpty = view.findViewById(R.id.tv_collab_empty);

        adapter = new CollabAdapter();
        rvCollab.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvCollab.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(TripCollabViewModel.class);
        viewModel.setTripId(tripId);

        btnAddCollaborator.setOnClickListener(v -> AddCollaboratorBottomSheet
                .newInstance(tripId)
                .show(getChildFragmentManager(), "AddCollaboratorBottomSheet"));

        viewModel.getTripMembers().observe(getViewLifecycleOwner(), members -> {
            List<TripMember> data = members == null ? Collections.emptyList() : members;
            String currentUserId = viewModel.getCurrentUserId();
            boolean isOwner = isCurrentUserOwner(data, currentUserId);

            adapter.setItems(data, currentUserId);
            adapter.setOwnerMode(isOwner);
            btnAddCollaborator.setVisibility(isOwner ? View.VISIBLE : View.GONE);
            tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        });

        adapter.setOnRemoveClickListener(member -> DialogUtils.showConfirmDialog(
                requireContext(),
                getString(R.string.trip_collab_remove_title),
                getString(R.string.trip_collab_remove_message, member.getName()),
                getString(R.string.remove),
                getString(R.string.cancel),
                () -> viewModel.removeCollaborator(member)
        ));
    }

    private boolean isCurrentUserOwner(@NonNull List<TripMember> members,
                                       @Nullable String currentUserId) {
        if (currentUserId == null || currentUserId.trim().isEmpty()) {
            return false;
        }
        String normalizedCurrentUserId = currentUserId.trim();
        for (TripMember member : members) {
            if (member == null || !member.isOwner()) {
                continue;
            }
            String memberId = member.getUserId();
            if (memberId != null && normalizedCurrentUserId.equals(memberId.trim())) {
                return true;
            }
        }
        return false;
    }

    private static class CollabAdapter extends RecyclerView.Adapter<CollabAdapter.CollabViewHolder> {

        interface OnRemoveClickListener {
            void onRemove(TripMember member);
        }

        private final List<TripMember> items = new ArrayList<>();
        private String currentUserId = "";
        private boolean isOwnerMode;
        private OnRemoveClickListener onRemoveClickListener;

        @NonNull
        @Override
        public CollabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_trip_collaborator, parent, false);
            return new CollabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CollabViewHolder holder, int position) {
            TripMember member = items.get(position);
            holder.bind(member, isOwnerMode, currentUserId, onRemoveClickListener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @SuppressLint("NotifyDataSetChanged")
        void setItems(@NonNull List<TripMember> data, @NonNull String currentUserId) {
            items.clear();
            items.addAll(data);
            this.currentUserId = currentUserId;
            notifyDataSetChanged();
        }

        @SuppressLint("NotifyDataSetChanged")
        void setOwnerMode(boolean ownerMode) {
            isOwnerMode = ownerMode;
            notifyDataSetChanged();
        }

        void setOnRemoveClickListener(OnRemoveClickListener listener) {
            this.onRemoveClickListener = listener;
        }

        static class CollabViewHolder extends RecyclerView.ViewHolder {
            final TextView tvAvatar;
            final TextView tvName;
            final TextView tvRole;
            final ImageButton btnDelete;

            CollabViewHolder(@NonNull View itemView) {
                super(itemView);
                tvAvatar = itemView.findViewById(R.id.tv_trip_member_avatar);
                tvName = itemView.findViewById(R.id.tv_trip_member_name);
                tvRole = itemView.findViewById(R.id.tv_trip_member_role);
                btnDelete = itemView.findViewById(R.id.btn_remove_trip_member);
            }

            void bind(TripMember member,
                      boolean ownerMode,
                      String currentUserId,
                      OnRemoveClickListener onRemoveClickListener) {
                if (member == null) {
                    return;
                }

                String safeName = member.getName() == null || member.getName().trim().isEmpty()
                        ? itemView.getContext().getString(R.string.trip_collab_member_unknown)
                        : member.getName().trim();

                String memberId = member.getUserId() == null ? "" : member.getUserId().trim();
                String normalizedCurrentUserId = currentUserId == null ? "" : currentUserId.trim();
                boolean isCurrentUser = !normalizedCurrentUserId.isEmpty()
                        && normalizedCurrentUserId.equals(memberId);

                String displayName = isCurrentUser
                        ? itemView.getContext().getString(R.string.member_you)
                        : safeName;

                String letter = member.getAvatarLetter();
                if (letter == null || letter.trim().isEmpty() || "?".equals(letter.trim())) {
                    letter = displayName.substring(0, 1).toUpperCase(Locale.ROOT);
                }

                tvAvatar.setText(letter);
                int avatarColor = member.getAvatarColor();
                if (avatarColor == 0) {
                    avatarColor = ContextCompat.getColor(itemView.getContext(), com.bif.app.core.R.color.primary_green);
                }
                tvAvatar.setBackgroundTintList(ColorStateList.valueOf(avatarColor));

                tvName.setText(displayName);
                tvRole.setText(member.isOwner()
                        ? R.string.trip_collab_role_owner
                        : R.string.trip_collab_role_collaborator);

                boolean showDelete = ownerMode && !member.isOwner();
                btnDelete.setVisibility(showDelete ? View.VISIBLE : View.GONE);
                btnDelete.setOnClickListener(v -> {
                    if (showDelete && onRemoveClickListener != null) {
                        onRemoveClickListener.onRemove(member);
                    }
                });
            }
        }
    }
}

