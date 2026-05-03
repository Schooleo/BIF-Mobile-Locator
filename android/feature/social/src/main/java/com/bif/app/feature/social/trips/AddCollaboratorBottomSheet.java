package com.bif.app.feature.social.trips;

import com.bif.app.feature.social.R;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.Friend;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class AddCollaboratorBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TRIP_ID = "tripId";

    private TripCollabViewModel viewModel;
    private FriendsInviteAdapter adapter;

    public static AddCollaboratorBottomSheet newInstance(String tripId) {
        AddCollaboratorBottomSheet sheet = new AddCollaboratorBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TRIP_ID, tripId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_add_collaborator, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.rv_available_friends);
        TextView emptyState = view.findViewById(R.id.tv_add_collaborator_empty);

        adapter = new FriendsInviteAdapter(friend -> {
            viewModel.addCollaborator(friend);
            dismiss();
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        Fragment parent = getParentFragment();
        if (parent == null) {
            dismiss();
            return;
        }

        viewModel = new ViewModelProvider(parent).get(TripCollabViewModel.class);
        String tripId = getArguments() != null ? getArguments().getString(ARG_TRIP_ID, "") : "";
        viewModel.setTripId(tripId);

        viewModel.getAvailableFriends().observe(getViewLifecycleOwner(), friends -> {
            List<Friend> data = friends != null ? friends : new ArrayList<>();
            adapter.submit(data);
            emptyState.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private static class FriendsInviteAdapter
            extends RecyclerView.Adapter<FriendsInviteAdapter.InviteViewHolder> {

        interface OnInviteListener {
            void onInvite(Friend friend);
        }

        private final OnInviteListener onInviteListener;
        private final List<Friend> items = new ArrayList<>();

        FriendsInviteAdapter(OnInviteListener onInviteListener) {
            this.onInviteListener = onInviteListener;
        }

        @NonNull
        @Override
        public InviteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_add_collaborator_friend, parent, false);
            return new InviteViewHolder(view, onInviteListener);
        }

        @Override
        public void onBindViewHolder(@NonNull InviteViewHolder holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        void submit(List<Friend> friends) {
            items.clear();
            if (friends != null) {
                items.addAll(friends);
            }
            notifyDataSetChanged();
        }

        static class InviteViewHolder extends RecyclerView.ViewHolder {

            private final TextView tvAvatar;
            private final TextView tvName;
            private final com.google.android.material.button.MaterialButton btnInvite;
            private final OnInviteListener onInviteListener;

            InviteViewHolder(@NonNull View itemView, OnInviteListener onInviteListener) {
                super(itemView);
                this.onInviteListener = onInviteListener;
                tvAvatar = itemView.findViewById(R.id.tv_friend_avatar);
                tvName = itemView.findViewById(R.id.tv_friend_name);
                btnInvite = itemView.findViewById(R.id.btn_invite_friend);
            }

            void bind(Friend friend) {
                String letter = friend.getAvatarLetter();
                if (letter == null || letter.trim().isEmpty()) {
                    String base = friend.getName() == null || friend.getName().trim().isEmpty()
                            ? "?"
                            : friend.getName().trim();
                    letter = base.substring(0, 1).toUpperCase();
                }
                tvAvatar.setText(letter);
                tvAvatar.setBackgroundTintList(ColorStateList.valueOf(friend.getAvatarColor()));

                String displayName = friend.getName();
                if (displayName == null || displayName.trim().isEmpty()) {
                    displayName = itemView.getContext().getString(R.string.trip_collab_member_unknown);
                }
                tvName.setText(displayName);
                btnInvite.setOnClickListener(v -> {
                    if (onInviteListener != null) {
                        onInviteListener.onInvite(friend);
                    }
                });
            }
        }
    }
}