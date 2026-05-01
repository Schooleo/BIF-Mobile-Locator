package com.bif.app.feature.social.friends;

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
    private static final int VIEW_TYPE_HEADER = 3;

    private List<Friend> friends = new ArrayList<>();
    private List<Friendship> pendingRequests = new ArrayList<>();
    private boolean isFriendRequestExpanded = true;
    private boolean isFriendListExpanded = true;
    private OnFriendActionListener listener;

    public interface OnFriendActionListener {
        void onAddFriendClick();
        void onAcceptRequestClick(Friendship friendship);
        void onRejectRequestClick(Friendship friendship);
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

        if (position == 1 || position == getFriendListHeaderPosition()) {
            return VIEW_TYPE_HEADER;
        }

        if (position < getFriendListHeaderPosition()) {
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
        } else if (viewType == VIEW_TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(com.bif.app.core.R.layout.component_section_header, parent, false);
            return new HeaderViewHolder(view);
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
        } else if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(position == 1);
        } else if (holder instanceof RequestViewHolder) {
            Friendship request = pendingRequests.get(position - 2);
            ((RequestViewHolder) holder).bind(request);
        } else if (holder instanceof FriendViewHolder) {
            int friendIndex = position - getFriendListHeaderPosition() - 1;
            Friend friend = friends.get(friendIndex);
            ((FriendViewHolder) holder).bind(friend, position);
        }
    }

    @Override
    public int getItemCount() {
        int requestCount = isFriendRequestExpanded ? pendingRequests.size() : 0;
        int friendCount = isFriendListExpanded ? friends.size() : 0;
        return 3 + requestCount + friendCount;
    }

    private int getFriendListHeaderPosition() {
        int requestCount = isFriendRequestExpanded ? pendingRequests.size() : 0;
        return 2 + requestCount;
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

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvSectionHeader;

        HeaderViewHolder(View itemView) {
            super(itemView);
            tvSectionHeader = itemView.findViewById(com.bif.app.core.R.id.tvSectionHeader);
        }

        void bind(boolean isRequestHeader) {
            boolean isExpanded = isRequestHeader ? isFriendRequestExpanded : isFriendListExpanded;
            int total = isRequestHeader ? pendingRequests.size() : friends.size();

            if (isExpanded) {
                tvSectionHeader.setText(isRequestHeader ? R.string.friend_request_header : R.string.friend_list_header);
            } else {
                tvSectionHeader.setText(itemView.getContext().getString(
                        isRequestHeader ? R.string.friend_request_header_with_total : R.string.friend_list_header_with_total,
                        total
                ));
            }

            tvSectionHeader.setOnClickListener(v -> {
                if (isRequestHeader) {
                    isFriendRequestExpanded = !isFriendRequestExpanded;
                } else {
                    isFriendListExpanded = !isFriendListExpanded;
                }
                notifyDataSetChanged();
            });
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
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(getVibrantColor(itemView, displayName.hashCode())));
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
        TextView tvAvatar, tvFriendName;
        ImageButton btnDelete;

        FriendViewHolder(View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(com.bif.app.core.R.id.tv_avatar);
            tvFriendName = itemView.findViewById(com.bif.app.core.R.id.tv_friend_name);
            btnDelete = itemView.findViewById(com.bif.app.core.R.id.btn_delete);
        }

        void bind(Friend friend, int position) {
            tvAvatar.setText(friend.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(getVibrantColor(itemView, friend.getName().hashCode())));
            tvFriendName.setText(friend.getName());

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteFriendClick(friend, position);
                }
            });
        }
    }

    private int getVibrantColor(View view, int seed) {
        int[] colors = {
                com.bif.app.core.R.color.avatar_red,
                com.bif.app.core.R.color.avatar_blue,
                com.bif.app.core.R.color.avatar_yellow,
                com.bif.app.core.R.color.avatar_purple,
                com.bif.app.core.R.color.avatar_orange,
                com.bif.app.core.R.color.avatar_teal,
                com.bif.app.core.R.color.avatar_indigo,
                com.bif.app.core.R.color.avatar_pink,
                com.bif.app.core.R.color.avatar_cyan
        };
        int colorRes = colors[Math.abs(seed) % colors.length];
        return view.getContext().getResources().getColor(colorRes, null);
    }
}
