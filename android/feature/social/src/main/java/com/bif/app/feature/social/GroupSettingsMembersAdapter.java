package com.bif.app.feature.social;

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

public class GroupSettingsMembersAdapter extends RecyclerView.Adapter<GroupSettingsMembersAdapter.MemberViewHolder> {

    public interface OnRemoveClickListener {
        void onRemove(Friend member);
    }

    private final OnRemoveClickListener onRemoveClickListener;
    private final List<Friend> members = new ArrayList<>();
    private boolean isOwner;

    public GroupSettingsMembersAdapter(OnRemoveClickListener onRemoveClickListener) {
        this.onRemoveClickListener = onRemoveClickListener;
    }

    public void submit(List<Friend> list, boolean owner) {
        members.clear();
        if (list != null) {
            members.addAll(list);
        }
        isOwner = owner;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group_settings_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        holder.bind(members.get(position), position);
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvAvatar;
        private final TextView tvName;
        private final TextView tvRole;
        private final ImageButton btnDelete;

        MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tv_member_avatar);
            tvName = itemView.findViewById(R.id.tv_member_name);
            tvRole = itemView.findViewById(R.id.tv_member_role);
            btnDelete = itemView.findViewById(R.id.btn_remove_member);
        }

        void bind(Friend member, int position) {
            tvAvatar.setText(member.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(member.getAvatarColor()));
            tvName.setText(position == 0 ? itemView.getContext().getString(R.string.member_you) : member.getName());
            tvRole.setText(position == 0 ? R.string.member_admin : R.string.member_role);

            boolean canDelete = isOwner && position > 0;
            btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> {
                if (canDelete && onRemoveClickListener != null) {
                    onRemoveClickListener.onRemove(member);
                }
            });
        }
    }
}
