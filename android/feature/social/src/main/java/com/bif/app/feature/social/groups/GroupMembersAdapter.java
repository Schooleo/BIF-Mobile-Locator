package com.bif.app.feature.social.groups;

import com.bif.app.feature.social.R;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.domain.model.Friend;

import java.util.ArrayList;
import java.util.List;

public class GroupMembersAdapter extends RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder> {

    private List<Friend> members = new ArrayList<>();
    private final OnMemberRemoveListener listener;
    private final boolean isOwner;

    public interface OnMemberRemoveListener {
        void onRemoveMember(Friend member, int position);
    }

    public GroupMembersAdapter(OnMemberRemoveListener listener, boolean isOwner) {
        this.listener = listener;
        this.isOwner = isOwner;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setMembers(List<Friend> members) {
        this.members = members != null ? members : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(com.bif.app.core.R.layout.component_friend_list_item, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        Friend member = members.get(position);
        holder.bind(member, position);
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    public class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvFriendName, tvStatus;
        View viewStatus;
        ImageButton btnDelete;

        MemberViewHolder(View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(com.bif.app.core.R.id.tv_avatar);
            tvFriendName = itemView.findViewById(com.bif.app.core.R.id.tv_friend_name);
            tvStatus = itemView.findViewById(com.bif.app.core.R.id.tv_status);
            viewStatus = itemView.findViewById(com.bif.app.core.R.id.view_status);
            btnDelete = itemView.findViewById(com.bif.app.core.R.id.btn_delete);
        }

        void bind(Friend member, int position) {
            tvAvatar.setText(member.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(member.getAvatarColor()));
            tvFriendName.setText(member.getName());
            tvStatus.setText(member.isOnline() ? R.string.online : R.string.offline);
            viewStatus.setVisibility(member.isOnline() ? View.VISIBLE : View.GONE);

            if (isOwner) {
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onRemoveMember(member, position);
                    }
                });
            } else {
                btnDelete.setVisibility(View.GONE);
            }
        }
    }
}
