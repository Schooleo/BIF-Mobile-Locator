package com.bif.app.feature.social.groups;

import com.bif.app.feature.social.R;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GroupDetailFragment extends Fragment {

    private GroupDetailViewModel viewModel;
    private GroupMembersAdapter membersAdapter;

    private TextView tvHeaderTitle;
    private TextView tvGroupAvatar;
    private EditText etGroupName;
    private Button btnSave;
    private TextView tvMembersHeader;
    private RecyclerView rvMembers;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ImageButton btnRenameGroup;
    private ImageButton btnDisband;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(GroupDetailViewModel.class);

        // Bind views
        tvHeaderTitle = view.findViewById(R.id.tv_header_title);
        tvGroupAvatar = view.findViewById(R.id.tv_group_avatar);
        etGroupName = view.findViewById(R.id.et_group_name);
        btnSave = view.findViewById(R.id.btn_save);
        tvMembersHeader = view.findViewById(R.id.tv_members_header);
        rvMembers = view.findViewById(R.id.rv_members);
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_group_detail);
        btnRenameGroup = view.findViewById(R.id.btn_rename_group);
        btnDisband = view.findViewById(R.id.btn_disband);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> navigateBackToGroups());

        // Load group from args
        Bundle args = getArguments();
        if (args != null) {
            String groupId = args.getString("groupId", "");
            viewModel.loadGroup(groupId);
        }

        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refreshGroup();
            swipeRefreshLayout.postDelayed(() -> {
                if (isAdded()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 1000L);
        });

        observeViewModel();
        setupSaveButton();
    }

    private void navigateBackToGroups() {
        // Set result so SocialFragment knows to show the Groups tab
        getParentFragmentManager().setFragmentResult("groupDetailResult",
                new Bundle());
        Navigation.findNavController(requireView()).navigateUp();
    }

    private void observeViewModel() {
        viewModel.getGroup().observe(getViewLifecycleOwner(), group -> {
            swipeRefreshLayout.setRefreshing(false);
            if (group == null) return;

            // Update header title with group name
            tvHeaderTitle.setText(group.getName());

            // Update avatar
            tvGroupAvatar.setText(group.getAvatarLetter());
            tvGroupAvatar.setBackgroundTintList(ColorStateList.valueOf(group.getAvatarColor()));

            // Update name field (only set if user hasn't started editing)
            if (!etGroupName.hasFocus()) {
                etGroupName.setText(group.getName());
            }

            // Update members header
            tvMembersHeader.setText(getString(R.string.members_header, group.getMemberCount()));

            // Show disband button only for owners
            if (group.isOwner()) {
                btnRenameGroup.setVisibility(View.VISIBLE);
                btnRenameGroup.setOnClickListener(v -> showRenameGroupDialog(group));
                btnDisband.setVisibility(View.VISIBLE);
                btnDisband.setOnClickListener(v -> confirmDisbandGroup(group));
            } else {
                btnRenameGroup.setVisibility(View.GONE);
                btnDisband.setVisibility(View.GONE);
            }

            // Setup members adapter
            setupMembersAdapter(group);
        });
    }

    private void setupMembersAdapter(Group group) {
        if (membersAdapter == null) {
            membersAdapter = new GroupMembersAdapter(
                    (member, position) -> confirmRemoveMember(member),
                    group.isOwner()
            );
            rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvMembers.setAdapter(membersAdapter);
        }
        membersAdapter.setMembers(group.getMembers());
    }

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> {
            String newName = etGroupName.getText().toString().trim();
            if (newName.isEmpty()) {
                AppSnackbar.show(requireContext(), R.string.enter_group_name);
                return;
            }
            try {
                viewModel.updateGroupName(newName);
                AppSnackbar.show(requireContext(), R.string.group_updated);
            } catch (IllegalStateException exception) {
                AppSnackbar.show(requireContext(), mapPolicyErrorToMessage(exception.getMessage()));
            }
            etGroupName.clearFocus();
        });
    }

    private void confirmRemoveMember(Friend member) {
        if (member == null) {
            showToast(R.string.group_update_failed);
            return;
        }

        String rawMemberName = member.getName();
        final String memberName = TextUtils.isEmpty(rawMemberName)
            ? getString(R.string.member_role)
            : rawMemberName;

        DialogUtils.showConfirmDialog(requireContext(),
                getString(R.string.remove_member),
                getString(R.string.remove_member_confirm, memberName),
                getString(R.string.remove),
                getString(R.string.cancel),
                () -> {
                    try {
                        if (!isAdded()) {
                            return;
                        }
                        viewModel.removeMember(member);
                        showToast(getString(R.string.member_removed, memberName));
                    } catch (IllegalStateException exception) {
                        showToast(mapPolicyErrorToMessage(exception.getMessage()));
                    } catch (Exception exception) {
                        showToast(R.string.group_update_failed);
                    }
                });
    }

    private void confirmDisbandGroup(Group group) {
        DialogUtils.showConfirmDialog(requireContext(),
                getString(R.string.disband_group),
                getString(R.string.disband_group_confirm, group.getName()),
                getString(R.string.disband),
                getString(R.string.cancel),
                () -> {
                    try {
                        viewModel.disbandGroup();
                        AppSnackbar.show(requireContext(), getString(R.string.group_disbanded));
                        navigateBackToGroups();
                    } catch (IllegalStateException exception) {
                        AppSnackbar.show(requireContext(), mapPolicyErrorToMessage(exception.getMessage()));
                    }
                });
    }

    private void showRenameGroupDialog(Group group) {
        if (group == null) {
            return;
        }

        EditText input = new EditText(requireContext());
        input.setText(group.getName());
        input.setSelection(input.getText().length());
        input.setSingleLine(true);
        input.setHint(R.string.group_name);

        int horizontalPadding = (int) (20 * requireContext().getResources().getDisplayMetrics().density);
        int verticalPadding = (int) (12 * requireContext().getResources().getDisplayMetrics().density);
        input.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.rename_group)
                .setView(input)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss())
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        AppSnackbar.show(requireContext(), R.string.enter_group_name);
                        return;
                    }
                    if (newName.equals(group.getName())) {
                        return;
                    }
                    try {
                        viewModel.updateGroupName(newName);
                        AppSnackbar.show(requireContext(), R.string.group_updated);
                    } catch (IllegalStateException exception) {
                        AppSnackbar.show(requireContext(), mapPolicyErrorToMessage(exception.getMessage()));
                    }
                })
                .show();
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

    private void showToast(int messageResId) {
        if (!isAdded() || getContext() == null) {
            return;
        }
        AppSnackbar.show(getContext(), messageResId);
    }

    private void showToast(String message) {
        if (!isAdded() || getContext() == null || message == null) {
            return;
        }
        AppSnackbar.show(getContext(), message);
    }
}
