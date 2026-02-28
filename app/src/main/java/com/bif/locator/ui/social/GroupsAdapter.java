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
import com.bif.locator.domain.model.Group;

import java.util.ArrayList;
import java.util.List;

public class GroupsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ACTION = 0;
    private static final int VIEW_TYPE_GROUP = 1;

    private List<Group> groups = new ArrayList<>();
    private OnGroupActionListener listener;

    public interface OnGroupActionListener {
        void onCreateGroupClick();
        void onGroupOptionsClick(Group group, int position);
    }

    public GroupsAdapter(OnGroupActionListener listener) {
        this.listener = listener;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? VIEW_TYPE_ACTION : VIEW_TYPE_GROUP;
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
                    .inflate(R.layout.component_group_list_item, parent, false);
            return new GroupViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof ActionViewHolder) {
            ((ActionViewHolder) holder).bind();
        } else if (holder instanceof GroupViewHolder) {
            Group group = groups.get(position - 1); // -1 because of action item
            ((GroupViewHolder) holder).bind(group, position);
        }
    }

    @Override
    public int getItemCount() {
        return groups.size() + 1; // +1 for action item
    }

    class ActionViewHolder extends RecyclerView.ViewHolder {
        TextView tvActionText;
        android.widget.ImageView ivActionIcon;

        ActionViewHolder(View itemView) {
            super(itemView);
            tvActionText = itemView.findViewById(R.id.tv_action_text);
            ivActionIcon = itemView.findViewById(R.id.iv_action_icon);
            
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCreateGroupClick();
                }
            });
        }

        void bind() {
            tvActionText.setText(R.string.create_new_group);
            ivActionIcon.setImageResource(R.drawable.ic_add_group);
        }
    }

    class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatar, tvGroupName, tvMembers;
        ImageButton btnMore;

        GroupViewHolder(View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tv_avatar);
            tvGroupName = itemView.findViewById(R.id.tv_group_name);
            tvMembers = itemView.findViewById(R.id.tv_members);
            btnMore = itemView.findViewById(R.id.btn_more);
        }

        void bind(Group group, int position) {
            tvAvatar.setText(group.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(group.getAvatarColor()));
            tvGroupName.setText(group.getName());
            tvMembers.setText(itemView.getContext().getString(R.string.members, group.getMemberCount()));

            btnMore.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGroupOptionsClick(group, position);
                }
            });
        }
    }
}
