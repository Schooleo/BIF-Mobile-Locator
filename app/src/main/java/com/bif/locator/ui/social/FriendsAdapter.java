package com.bif.locator.ui.social;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.locator.R;
import com.bif.locator.domain.model.Friend;

import java.util.ArrayList;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ACTION = 0;
    private static final int VIEW_TYPE_FRIEND = 1;

    private List<Friend> friends = new ArrayList<>();
    private OnFriendActionListener listener;

    public interface OnFriendActionListener {
        void onAddFriendClick();
        void onDeleteFriendClick(Friend friend, int position);
    }

    public FriendsAdapter(OnFriendActionListener listener) {
        this.listener = listener;
    }

    public void setFriends(List<Friend> friends) {
        this.friends = friends;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_ACTION : VIEW_TYPE_FRIEND;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ACTION) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.component_action_list_item, parent, false);
            return new ActionViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.component_friend_list_item, parent, false);
            return new FriendViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ActionViewHolder) {
            ((ActionViewHolder) holder).bind();
        } else if (holder instanceof FriendViewHolder) {
            Friend friend = friends.get(position - 1); // -1 because of action item
            ((FriendViewHolder) holder).bind(friend, position);
        }
    }

    @Override
    public int getItemCount() {
        return friends.size() + 1; // +1 for action item
    }

    class ActionViewHolder extends RecyclerView.ViewHolder {
        ActionViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddFriendClick();
                }
            });
        }

        void bind() {
            // Action item is already set in XML
        }
    }

    class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvFriendName, tvStatus;
        View viewStatus;
        ImageButton btnDelete;

        FriendViewHolder(View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tv_avatar);
            tvFriendName = itemView.findViewById(R.id.tv_friend_name);
            tvStatus = itemView.findViewById(R.id.tv_status);
            viewStatus = itemView.findViewById(R.id.view_status);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }

        void bind(Friend friend, int position) {
            tvAvatar.setText(friend.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(friend.getAvatarColor()));
            tvFriendName.setText(friend.getName());
            tvStatus.setText(friend.isOnline() ? R.string.online : R.string.offline);
            viewStatus.setVisibility(friend.isOnline() ? View.VISIBLE : View.GONE);

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteFriendClick(friend, position);
                }
            });
        }
    }
}
