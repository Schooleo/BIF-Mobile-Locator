package com.bif.app.feature.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Friendship;
import com.bif.app.domain.model.Group;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class SocialFragment extends Fragment {

    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProgressBar progressLoading;
    private LinearLayout stateLayout;
    private TextView tvStateMessage;
    private Button btnRetry;
    private FriendsAdapter friendsAdapter;
    private GroupsAdapter groupsAdapter;
    private SocialViewModel viewModel;
    private boolean isFriendActionLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_social, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.retryFriends();
            viewModel.refreshRequestsOnly();
            viewModel.retryGroups();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && viewModel != null) {
            viewModel.retryFriends();
            viewModel.refreshRequestsOnly();
            viewModel.retryGroups();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SocialViewModel.class);

        tabLayout = view.findViewById(R.id.tab_layout);
        recyclerView = view.findViewById(R.id.recycler_view);
        progressLoading = view.findViewById(R.id.progress_loading);
        stateLayout = view.findViewById(R.id.layout_state);
        tvStateMessage = view.findViewById(R.id.tv_state_message);
        btnRetry = view.findViewById(R.id.btn_retry);

        btnRetry.setOnClickListener(v -> {
            if (tabLayout.getSelectedTabPosition() == 0) {
                viewModel.retryFriends();
            } else {
                viewModel.retryGroups();
            }
            renderCurrentTabState();
        });

        setupRecyclerView();
        setupTabs();

        observeViewModel();

        // Listen for result from GroupDetailFragment to switch to Groups tab
        getParentFragmentManager().setFragmentResultListener("groupDetailResult",
                getViewLifecycleOwner(), (requestKey, result) -> {
                    TabLayout.Tab groupsTab = tabLayout.getTabAt(1);
                    if (groupsTab != null) {
                        groupsTab.select();
                    }
                });
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Setup Friends Adapter
        friendsAdapter = new FriendsAdapter(new FriendsAdapter.OnFriendActionListener() {
            @Override
            public void onAddFriendClick() {
                showAddFriendDialog();
            }

            @Override
            public void onAcceptRequestClick(Friendship friendship) {
                friendsAdapter.removePendingRequestOptimistically(friendship.getId());
                viewModel.acceptFriendRequest(friendship.getId());
            }

            @Override
            public void onRejectRequestClick(Friendship friendship) {
                friendsAdapter.removePendingRequestOptimistically(friendship.getId());
                viewModel.rejectFriendRequest(friendship.getId());
            }

            @Override
            public void onFriendClick(Friend friend) {
                navigateToChatFromFriend(friend);
            }

            @Override
            public void onDeleteFriendClick(Friend friend, int position) {
                DialogUtils.showConfirmDialog(requireContext(),
                        getString(R.string.unfriend_title),
                        getString(R.string.unfriend_confirm_message, friend.getName()),
                        getString(R.string.unfriend_action),
                        getString(R.string.cancel),
                        () -> {
                            friendsAdapter.removeFriendOptimistically(friend.getId());
                            viewModel.deleteFriend(friend);
                        });
            }
        });

        // Setup Groups Adapter
        groupsAdapter = new GroupsAdapter(new GroupsAdapter.OnGroupActionListener() {
            @Override
            public void onCreateGroupClick() {
                showCreateGroupWithFriendsDialog();
            }

            @Override
            public void onGroupClick(Group group) {
                navigateToChatFromGroup(group);
            }

            @Override
            public void onGroupOptionsClick(Group group, int position) {
                handleGroupOptions(group);
            }
        });

        recyclerView.setAdapter(friendsAdapter);
    }

    private void observeViewModel() {
        viewModel.getFriendActionMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                String toastMessage = message;
                if ("__MSG_USER_NOT_FOUND__".equals(message)) {
                    toastMessage = getString(R.string.user_not_found);
                } else if ("__MSG_FRIEND_REQUEST_SENT__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_sent);
                } else if ("__MSG_FRIEND_REQUEST_SELF__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_self_not_allowed);
                } else if ("__MSG_FRIEND_REQUEST_PENDING__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_pending_exists);
                } else if ("__MSG_FRIEND_REQUEST_ALREADY_FRIENDS__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_already_friends);
                } else if ("__MSG_FRIEND_REQUEST_SEND_FAILED__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_send_failed);
                } else if ("__MSG_FRIEND_REQUEST_ACCEPT_SUCCESS__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_accepted);
                } else if ("__MSG_FRIEND_REQUEST_REJECT_SUCCESS__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_rejected);
                } else if ("__MSG_FRIEND_REQUEST_ACCEPT_FAILED__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_accept_failed);
                    viewModel.refreshRequestsOnly();
                } else if ("__MSG_FRIEND_REQUEST_REJECT_FAILED__".equals(message)) {
                    toastMessage = getString(R.string.friend_request_reject_failed);
                    viewModel.refreshRequestsOnly();
                } else if ("__MSG_UNFRIEND_SUCCESS__".equals(message)) {
                    toastMessage = getString(R.string.unfriend_success);
                } else if ("__MSG_UNFRIEND_FAILED__".equals(message)) {
                    toastMessage = getString(R.string.unfriend_failed);
                    viewModel.retryFriends();
                }
                Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_SHORT).show();
                viewModel.clearFriendActionMessage();
            }
        });

        viewModel.getFriendActionLoading().observe(getViewLifecycleOwner(), isLoading -> {
            isFriendActionLoading = isLoading != null && isLoading;
            updateFriendActionLoadingUi();
        });

        viewModel.getPendingRequests().observe(getViewLifecycleOwner(), requests ->
                friendsAdapter.setPendingRequests(requests != null ? requests : new ArrayList<>()));

        viewModel.getFriendUiState().observe(getViewLifecycleOwner(), state -> {
            if (tabLayout.getSelectedTabPosition() == 0) {
                renderFriendState(state);
            }
        });

        viewModel.getGroupUiState().observe(getViewLifecycleOwner(), state -> {
            if (tabLayout.getSelectedTabPosition() == 1) {
                renderGroupState(state);
            }
        });
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                renderCurrentTabState();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        renderCurrentTabState();
    }

    private void renderCurrentTabState() {
        if (tabLayout.getSelectedTabPosition() == 0) {
            renderFriendState(viewModel.getFriendUiState().getValue());
        } else {
            renderGroupState(viewModel.getGroupUiState().getValue());
        }
    }

    private void renderFriendState(UiState<List<Friend>> state) {
        if (state == null || state instanceof UiState.Loading) {
            showLoading();
            return;
        }
        if (state instanceof UiState.Empty) {
            // Keep list visible so the first action row (Add New Friend) is always accessible.
            friendsAdapter.setFriends(new ArrayList<>());
            showList(friendsAdapter);
            return;
        }
        if (state instanceof UiState.Error) {
            UiState.Error<List<Friend>> error = (UiState.Error<List<Friend>>) state;
            showState(error.getMessage(), true);
            return;
        }

        UiState.Success<List<Friend>> success = (UiState.Success<List<Friend>>) state;
        friendsAdapter.setFriends(success.getData());
        showList(friendsAdapter);
    }

    private void renderGroupState(UiState<List<Group>> state) {
        if (state == null || state instanceof UiState.Loading) {
            showLoading();
            return;
        }
        if (state instanceof UiState.Empty) {
            // Keep list visible so the action row (Create New Group) is always accessible.
            groupsAdapter.setGroups(new ArrayList<>());
            showList(groupsAdapter);
            return;
        }
        if (state instanceof UiState.Error) {
            UiState.Error<List<Group>> error = (UiState.Error<List<Group>>) state;
            showState(error.getMessage(), true);
            return;
        }

        UiState.Success<List<Group>> success = (UiState.Success<List<Group>>) state;
        groupsAdapter.setGroups(success.getData());
        showList(groupsAdapter);
    }

    private void showLoading() {
        recyclerView.setVisibility(View.GONE);
        stateLayout.setVisibility(View.GONE);
        progressLoading.setVisibility(View.VISIBLE);
        recyclerView.setOnTouchListener(null);
        recyclerView.setAlpha(1f);
    }

    private void showState(String message, boolean showRetry) {
        recyclerView.setVisibility(View.GONE);
        progressLoading.setVisibility(View.GONE);
        stateLayout.setVisibility(View.VISIBLE);
        tvStateMessage.setText(message);
        btnRetry.setVisibility(showRetry ? View.VISIBLE : View.GONE);
        recyclerView.setOnTouchListener(null);
        recyclerView.setAlpha(1f);
    }

    private void showList(RecyclerView.Adapter<?> adapter) {
        progressLoading.setVisibility(View.GONE);
        stateLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        recyclerView.setAdapter(adapter);
        updateFriendActionLoadingUi();
    }

    private void updateFriendActionLoadingUi() {
        boolean isFriendTab = tabLayout != null && tabLayout.getSelectedTabPosition() == 0;
        boolean canOverlay = isFriendTab && recyclerView.getVisibility() == View.VISIBLE;

        if (canOverlay && isFriendActionLoading) {
            progressLoading.setVisibility(View.VISIBLE);
            recyclerView.setAlpha(0.5f);
            recyclerView.setOnTouchListener((v, event) -> true);
        } else {
            if (canOverlay) {
                progressLoading.setVisibility(View.GONE);
            }
            recyclerView.setAlpha(1f);
            recyclerView.setOnTouchListener(null);
        }
    }

    private void showAddFriendDialog() {
        DialogUtils.showCustomInputDialog(
                requireContext(),
                R.layout.dialog_add_friend,
                R.id.btn_add_friend,
                R.id.et_search,
                R.id.btn_close,
                inputText -> {
                    if (!inputText.isEmpty()) {
                        viewModel.addFriend(inputText);
                    } else {
                        Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showCreateGroupWithFriendsDialog() {
        List<Friend> currentFriends = viewModel.getFriends().getValue();
        List<Friend> friendList = currentFriends != null ? currentFriends : new ArrayList<>();

        DialogUtils.showCustomViewDialog(
                requireContext(),
                R.layout.dialog_create_group,
                R.id.btn_close,
                (dialogView, dialog) -> {
                    EditText etGroupName = dialogView.findViewById(R.id.et_group_name);
                    RecyclerView rvFriends = dialogView.findViewById(R.id.rv_friends_select);
                    Button btnCreate = dialogView.findViewById(R.id.btn_create_group);

                    SelectFriendAdapter selectAdapter = new SelectFriendAdapter(friendList);
                    rvFriends.setLayoutManager(new LinearLayoutManager(requireContext()));
                    rvFriends.setAdapter(selectAdapter);

                    btnCreate.setOnClickListener(v -> {
                        String groupName = etGroupName.getText().toString().trim();
                        List<Friend> selectedFriends = selectAdapter.getSelectedFriends();

                        if (groupName.isEmpty()) {
                            Toast.makeText(requireContext(), "Please input group name", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (selectedFriends.isEmpty()) {
                            Toast.makeText(requireContext(), "Please choose at least 1 member", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        viewModel.createGroup(groupName, selectedFriends);
                        Toast.makeText(requireContext(), "Group created: " + groupName, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    });
                }
        );
    }

    private void handleGroupOptions(Group group) {
        String title = group.isOwner() ? "Disband Group" : "Leave Group";
        String message = group.isOwner()
                ? "Do you want to disband the group '" + group.getName() + "'?"
                : "Do you want to leave the group '" + group.getName() + "'?";
        String actionBtn = group.isOwner() ? "Disband" : "Leave";

        DialogUtils.showConfirmDialog(requireContext(),
                title,
                message,
                actionBtn,
                "Cancel",
                () -> {
                    viewModel.handleGroupAction(group);
                    Toast.makeText(requireContext(), group.isOwner() ? "Disbanded" : "Left", Toast.LENGTH_SHORT).show();
                });
    }

    private void navigateToChatFromFriend(Friend friend) {
        android.net.Uri destUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT).buildUpon()
                .appendQueryParameter("chatType", "friend")
                .appendQueryParameter("chatId", String.valueOf(friend.getId()))
                .appendQueryParameter("chatName", friend.getName())
                .appendQueryParameter("avatarLetter", friend.getAvatarLetter())
                .appendQueryParameter("avatarColor", String.valueOf(friend.getAvatarColor()))
                .appendQueryParameter("memberCount", "0")
                .build();
        Navigation.findNavController(requireView()).navigate(destUri);
    }

    private void navigateToChatFromGroup(Group group) {
        android.net.Uri destUri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT).buildUpon()
                .appendQueryParameter("chatType", "group")
                .appendQueryParameter("chatId", group.getServerId())
                .appendQueryParameter("chatName", group.getName())
                .appendQueryParameter("avatarLetter", group.getAvatarLetter())
                .appendQueryParameter("avatarColor", String.valueOf(group.getAvatarColor()))
                .appendQueryParameter("memberCount", String.valueOf(group.getMemberCount()))
                .build();
        Navigation.findNavController(requireView()).navigate(destUri);
    }
}
