package com.bif.app.feature.social.friends;

import com.bif.app.feature.social.R;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.Friend;

import java.util.ArrayList;
import java.util.List;

public class SelectFriendAdapter extends RecyclerView.Adapter<SelectFriendAdapter.SelectFriendViewHolder> {

    private final List<Friend> friends;
    private final List<Friend> selectedFriends;

    public SelectFriendAdapter(List<Friend> friends) {
        this.friends = friends != null ? friends : new ArrayList<>();
        this.selectedFriends = new ArrayList<>();
    }

    @NonNull
    @Override
    public SelectFriendViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_selectable, parent, false);
        return new SelectFriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectFriendViewHolder holder, int position) {
        Friend friend = friends.get(position);

        holder.tvFriendName.setText(friend.getName());

        holder.cbSelect.setOnCheckedChangeListener(null);

        holder.cbSelect.setChecked(selectedFriends.contains(friend));

        holder.cbSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!selectedFriends.contains(friend)) {
                    selectedFriends.add(friend);
                }
            } else {
                selectedFriends.remove(friend);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            boolean currentState = holder.cbSelect.isChecked();
            holder.cbSelect.setChecked(!currentState);
        });
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    public List<Friend> getSelectedFriends() {
        return selectedFriends;
    }

    public static class SelectFriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvFriendName;
        CheckBox cbSelect;

        public SelectFriendViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFriendName = itemView.findViewById(R.id.tv_friend_name);
            cbSelect = itemView.findViewById(R.id.cb_select);
        }
    }
}