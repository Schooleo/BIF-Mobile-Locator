package com.bif.app.feature.social.groups;

import com.bif.app.feature.social.R;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GroupSettingsMembersAdapter extends RecyclerView.Adapter<GroupSettingsMembersAdapter.MemberViewHolder> {

    public interface OnRemoveClickListener {
        void onRemove(Friend member);
    }

    public interface OnMemberClickListener {
        void onMemberClick(Friend member, int position);
    }

    private final OnRemoveClickListener onRemoveClickListener;
    private final OnMemberClickListener onMemberClickListener;
    private final List<Friend> members = new ArrayList<>();
    private final Map<Integer, String> memberRoles = new HashMap<>();
    private boolean isOwner;

    public GroupSettingsMembersAdapter(OnRemoveClickListener onRemoveClickListener,
                                       OnMemberClickListener onMemberClickListener) {
        this.onRemoveClickListener = onRemoveClickListener;
        this.onMemberClickListener = onMemberClickListener;
    }

    public void submit(List<Friend> list, boolean owner, Map<Integer, String> roles) {
        members.clear();
        if (list != null) {
            members.addAll(list);
        }
        memberRoles.clear();
        if (roles != null) {
            memberRoles.putAll(roles);
        }
        isOwner = owner;
        notifyDataSetChanged();
    }

    public void updateMemberRole(int memberId, String role) {
        memberRoles.put(memberId, role);
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

            String role = memberRoles.get(member.getId());
            boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || position == 0;
            tvRole.setText(isAdmin ? R.string.member_admin : R.string.member_role);

            boolean canDelete = isOwner && position > 0;
            btnDelete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> {
                if (canDelete && onRemoveClickListener != null) {
                    onRemoveClickListener.onRemove(member);
                }
            });

            itemView.setOnClickListener(v -> {
                if (onMemberClickListener != null) {
                    onMemberClickListener.onMemberClick(member, position);
                }
            });
        }
    }
}
