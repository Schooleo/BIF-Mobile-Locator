package com.bif.app.feature.social;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.feature.social.R;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ACTION = 0;
    private static final int VIEW_TYPE_REQUEST = 1;
    private static final int VIEW_TYPE_FRIEND = 2;

    private List<Friend> friends = new ArrayList<>();
    private List<Friendship> pendingRequests = new ArrayList<>();
    private OnFriendActionListener listener;

    public interface OnFriendActionListener {
        void onAddFriendClick();
        void onAcceptRequestClick(Friendship friendship);
        void onRejectRequestClick(Friendship friendship);
        void onFriendClick(Friend friend);
        void onDeleteFriendClick(Friend friend, int position);
    }

    public FriendsAdapter(OnFriendActionListener listener) {
        this.listener = listener;
    }

    public void setFriends(List<Friend> friends) {
        this.friends = friends;
        notifyDataSetChanged();
    }

    public void setPendingRequests(List<Friendship> pendingRequests) {
        this.pendingRequests = pendingRequests;
        notifyDataSetChanged();
    }

    public void removePendingRequestOptimistically(int friendshipId) {
        if (friendshipId <= 0 || pendingRequests == null || pendingRequests.isEmpty()) {
            return;
        }

        for (int index = 0; index < pendingRequests.size(); index++) {
            Friendship item = pendingRequests.get(index);
            if (item != null && item.getId() == friendshipId) {
                pendingRequests.remove(index);
                notifyDataSetChanged();
                return;
            }
        }
    }

    public void removeFriendOptimistically(int friendId) {
        if (friendId <= 0 || friends == null || friends.isEmpty()) {
            return;
        }

        for (int index = 0; index < friends.size(); index++) {
            Friend item = friends.get(index);
            if (item != null && item.getId() == friendId) {
                friends.remove(index);
                notifyDataSetChanged();
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_ACTION;
        }

        if (position <= pendingRequests.size()) {
            return VIEW_TYPE_REQUEST;
        }

        return VIEW_TYPE_FRIEND;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ACTION) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(com.bif.app.core.R.layout.component_action_list_item, parent, false);
            return new ActionViewHolder(view);
        } else if (viewType == VIEW_TYPE_REQUEST) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(com.bif.app.core.R.layout.component_friend_request_list_item, parent, false);
            return new RequestViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(com.bif.app.core.R.layout.component_friend_list_item, parent, false);
            return new FriendViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ActionViewHolder) {
            ((ActionViewHolder) holder).bind();
        } else if (holder instanceof RequestViewHolder) {
            Friendship request = pendingRequests.get(position - 1);
            ((RequestViewHolder) holder).bind(request);
        } else if (holder instanceof FriendViewHolder) {
            int friendIndex = position - pendingRequests.size() - 1;
            Friend friend = friends.get(friendIndex);
            ((FriendViewHolder) holder).bind(friend, position);
        }
    }

    @Override
    public int getItemCount() {
        return 1 + pendingRequests.size() + friends.size();
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
            // Default action item content in XML is Add New Friend.
        }
    }

    class RequestViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar;
        TextView tvRequestLabel;
        TextView tvRequestName;
        ImageButton btnAccept;
        ImageButton btnReject;

        RequestViewHolder(View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(com.bif.app.core.R.id.tv_request_avatar);
            tvRequestLabel = itemView.findViewById(com.bif.app.core.R.id.tv_request_label);
            tvRequestName = itemView.findViewById(com.bif.app.core.R.id.tv_request_name);
            btnAccept = itemView.findViewById(com.bif.app.core.R.id.btn_accept_request);
            btnReject = itemView.findViewById(com.bif.app.core.R.id.btn_reject_request);
        }

        void bind(Friendship friendship) {
            String requesterName = friendship.getRequesterName();
            String displayName = requesterName == null || requesterName.trim().isEmpty()
                    ? (friendship.getRequesterId() == null || friendship.getRequesterId().isEmpty() ? itemView.getContext().getString(R.string.chat_friend_name) : friendship.getRequesterId())
                    : requesterName;
            String avatarLetter = displayName.substring(0, 1).toUpperCase(Locale.ROOT);

            tvAvatar.setText(avatarLetter);
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(
                    itemView.getResources().getColor(com.bif.app.core.R.color.avatar_red, null)
            ));
            tvRequestLabel.setText(R.string.friend_request_from);
            tvRequestName.setText(displayName);

            btnAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAcceptRequestClick(friendship);
                }
            });

            btnReject.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRejectRequestClick(friendship);
                }
            });
        }
    }

    class FriendViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvFriendName, tvStatus;
        View viewStatus;
        ImageButton btnDelete;

        FriendViewHolder(View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(com.bif.app.core.R.id.tv_avatar);
            tvFriendName = itemView.findViewById(com.bif.app.core.R.id.tv_friend_name);
            tvStatus = itemView.findViewById(com.bif.app.core.R.id.tv_status);
            viewStatus = itemView.findViewById(com.bif.app.core.R.id.view_status);
            btnDelete = itemView.findViewById(com.bif.app.core.R.id.btn_delete);
        }

        void bind(Friend friend, int position) {
            tvAvatar.setText(friend.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(friend.getAvatarColor()));
            tvFriendName.setText(friend.getName());
            tvStatus.setText(friend.isOnline() ? R.string.online : R.string.offline);
            viewStatus.setVisibility(friend.isOnline() ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onFriendClick(friend);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteFriendClick(friend, position);
                }
            });
        }
    }
}
