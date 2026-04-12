package com.bif.app.feature.social;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GroupSettingsMembersFragment extends Fragment {

    private GroupDetailViewModel viewModel;
    private GroupSettingsMembersAdapter membersAdapter;
    private String groupId;
    private ProgressBar progressMembers;
    private TextView tvMembersState;
    private Group currentGroup;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_settings_members, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(GroupDetailViewModel.class);
        groupId = getArguments() != null ? getArguments().getString("groupId", "") : "";

        TextView tvAvatar = view.findViewById(R.id.tv_group_avatar);
        TextView tvName = view.findViewById(R.id.tv_group_name);
        TextView tvMemberCount = view.findViewById(R.id.tv_member_count);
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        MaterialButton btnAdd = view.findViewById(R.id.btn_add_member);
        TabLayout tabLayout = view.findViewById(R.id.tab_group_settings);
        RecyclerView rvMembers = view.findViewById(R.id.rv_group_members);
        progressMembers = view.findViewById(R.id.progress_members);
        tvMembersState = view.findViewById(R.id.tv_members_state);

        btnBack.setOnClickListener(v -> navigateBackToGroupChat(view));
        btnAdd.setOnClickListener(v -> showAddMembersDialog());

        setupTabs(tabLayout, 2, view);

        membersAdapter = new GroupSettingsMembersAdapter(this::confirmRemoveMember, this::showUpdateRoleDialog);
        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMembers.setAdapter(membersAdapter);
        showMembersLoading(rvMembers);

        viewModel.loadGroup(groupId);
        viewModel.getGroup().observe(getViewLifecycleOwner(), group -> {
            if (group == null) {
                showMembersError(rvMembers, getString(R.string.group_members_error));
                return;
            }
            currentGroup = group;
            tvAvatar.setText(group.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(group.getAvatarColor()));
            tvName.setText(group.getName());
            tvMemberCount.setText(getString(R.string.chat_member_count, group.getMemberCount()));
            membersAdapter.submit(group.getMembers(), group.isOwner(), group.getMemberRoles());

            if (group.getMembers() == null || group.getMembers().isEmpty()) {
                showMembersEmpty(rvMembers, getString(R.string.group_members_empty));
                return;
            }

            showMembersList(rvMembers);
        });
    }

    private void showUpdateRoleDialog(Friend member, int position) {
        if (currentGroup == null || member == null || !currentGroup.isOwner()) {
            return;
        }

        if (position == 0) {
            AppSnackbar.show(requireContext(), R.string.group_role_owner_fixed);
            return;
        }

        Map<Integer, String> roleMap = currentGroup.getMemberRoles();
        String currentRole = roleMap != null ? roleMap.get(member.getId()) : null;
        String normalizedCurrentRole = "ADMIN".equalsIgnoreCase(currentRole) ? "ADMIN" : "MEMBER";

        String[] roleItems = new String[]{
                getString(R.string.member_admin),
                getString(R.string.member_role)
        };
        int checkedItem = "ADMIN".equals(normalizedCurrentRole) ? 0 : 1;
        final int[] selectedItem = new int[]{checkedItem};

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.group_role_dialog_title, member.getName()))
                .setSingleChoiceItems(roleItems, checkedItem, (dialog, which) -> selectedItem[0] = which)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newRole = selectedItem[0] == 0 ? "ADMIN" : "MEMBER";
                    if (newRole.equals(normalizedCurrentRole)) {
                        return;
                    }

                    viewModel.updateMemberRole(member, newRole);
                    membersAdapter.updateMemberRole(member.getId(), newRole);
                    if (currentGroup.getMemberRoles() != null) {
                        currentGroup.getMemberRoles().put(member.getId(), newRole);
                    }

                    AppSnackbar.show(requireContext(), getString(R.string.group_role_updated, member.getName(), roleItems[selectedItem[0]]));
                })
                .show();
    }

    private void showAddMembersDialog() {
        if (currentGroup == null) {
            AppSnackbar.show(requireContext(), R.string.group_members_error);
            return;
        }

        List<Friend> allFriends = viewModel.getFriends().getValue();
        if (allFriends == null || allFriends.isEmpty()) {
            AppSnackbar.show(requireContext(), R.string.group_add_member_no_friends);
            return;
        }

        Set<Integer> existingMemberIds = new HashSet<>();
        List<Friend> existingMembers = currentGroup.getMembers();
        if (existingMembers != null) {
            for (Friend member : existingMembers) {
                if (member != null) {
                    existingMemberIds.add(member.getId());
                }
            }
        }

        List<Friend> availableFriends = new ArrayList<>();
        for (Friend friend : allFriends) {
            if (friend != null && !existingMemberIds.contains(friend.getId())) {
                availableFriends.add(friend);
            }
        }

        if (availableFriends.isEmpty()) {
            AppSnackbar.show(requireContext(), R.string.group_add_member_none_available);
            return;
        }

        String[] friendNames = new String[availableFriends.size()];
        boolean[] checkedItems = new boolean[availableFriends.size()];
        for (int i = 0; i < availableFriends.size(); i++) {
            friendNames[i] = availableFriends.get(i).getName();
            checkedItems[i] = false;
        }

        List<Friend> selectedFriends = new ArrayList<>();
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.group_add_member_dialog_title)
                .setMultiChoiceItems(friendNames, checkedItems, (dialog, which, isChecked) -> {
                    Friend selected = availableFriends.get(which);
                    if (isChecked) {
                        if (!selectedFriends.contains(selected)) {
                            selectedFriends.add(selected);
                        }
                    } else {
                        selectedFriends.remove(selected);
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.group_add_member_confirm, (dialog, which) -> {
                    if (selectedFriends.isEmpty()) {
                        AppSnackbar.show(requireContext(), R.string.group_add_member_pick_one);
                        return;
                    }
                    viewModel.addMembers(selectedFriends);
                    AppSnackbar.show(requireContext(), getString(R.string.group_add_member_success_count, selectedFriends.size()));
                })
                .show();
    }

    private void confirmRemoveMember(Friend member) {
        if (member == null) {
            AppSnackbar.show(requireContext(), R.string.group_update_failed);
            return;
        }

        DialogUtils.showConfirmDialog(requireContext(),
                getString(R.string.remove_member),
                getString(R.string.remove_member_confirm, member.getName()),
                getString(R.string.remove),
                getString(R.string.cancel),
                () -> {
                    try {
                        viewModel.removeMember(member);
                        AppSnackbar.show(requireContext(), getString(R.string.member_removed, member.getName()));
                    } catch (IllegalStateException exception) {
                        AppSnackbar.show(requireContext(), mapPolicyErrorToMessage(exception.getMessage()));
                    } catch (Exception exception) {
                        AppSnackbar.show(requireContext(), R.string.group_update_failed);
                    }
                });
    }

    private int mapPolicyErrorToMessage(String code) {
        if ("GROUP_CREATE_REQUIRES_ONLINE".equals(code)) {
            return R.string.group_create_requires_online;
        }
        if ("GROUP_DELETE_REQUIRES_ONLINE".equals(code)) {
            return R.string.group_delete_requires_online;
        }
        if ("GROUP_REMOVE_MEMBER_REQUIRES_ONLINE".equals(code)) {
            return R.string.group_remove_member_requires_online;
        }
        return R.string.group_update_failed;
    }

    private void setupTabs(TabLayout tabLayout, int selectedPosition, View rootView) {
        TabLayout.Tab selected = tabLayout.getTabAt(selectedPosition);
        if (selected != null) {
            selected.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == selectedPosition) {
                    return;
                }
                UriUtils.PathTo dest = position == 0
                        ? UriUtils.PathTo.GROUP_SETTINGS_PLANS
                        : UriUtils.PathTo.GROUP_SETTINGS_LOCATIONS;
                android.net.Uri uri = UriUtils.buildUri(dest)
                        .buildUpon()
                        .appendQueryParameter("groupId", groupId)
                        .build();
                Navigation.findNavController(rootView).navigate(uri);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    private void navigateBackToGroupChat(View rootView) {
        Group group = viewModel.getGroup().getValue();

        android.net.Uri.Builder uriBuilder = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT)
                .buildUpon()
                .appendQueryParameter("chatType", "group")
                .appendQueryParameter("chatId", groupId);

        if (group != null) {
            uriBuilder
                    .appendQueryParameter("chatName", group.getName())
                    .appendQueryParameter("avatarLetter", group.getAvatarLetter())
                    .appendQueryParameter("avatarColor", String.valueOf(group.getAvatarColor()))
                    .appendQueryParameter("memberCount", String.valueOf(group.getMemberCount()));
        }

        Navigation.findNavController(rootView).navigate(uriBuilder.build());
    }

    private void showMembersLoading(RecyclerView rvMembers) {
        rvMembers.setVisibility(View.GONE);
        tvMembersState.setVisibility(View.GONE);
        progressMembers.setVisibility(View.VISIBLE);
    }

    private void showMembersError(RecyclerView rvMembers, String message) {
        rvMembers.setVisibility(View.GONE);
        progressMembers.setVisibility(View.GONE);
        tvMembersState.setVisibility(View.VISIBLE);
        tvMembersState.setText(message);
    }

    private void showMembersEmpty(RecyclerView rvMembers, String message) {
        rvMembers.setVisibility(View.GONE);
        progressMembers.setVisibility(View.GONE);
        tvMembersState.setVisibility(View.VISIBLE);
        tvMembersState.setText(message);
    }

    private void showMembersList(RecyclerView rvMembers) {
        progressMembers.setVisibility(View.GONE);
        tvMembersState.setVisibility(View.GONE);
        rvMembers.setVisibility(View.VISIBLE);
    }

}
