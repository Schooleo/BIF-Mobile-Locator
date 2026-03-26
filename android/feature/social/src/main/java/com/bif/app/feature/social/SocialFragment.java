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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_social, container, false);
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
            public void onFriendClick(Friend friend) {
                navigateToChatFromFriend(friend);
            }

            @Override
            public void onDeleteFriendClick(Friend friend, int position) {
                DialogUtils.showConfirmDialog(requireContext(),
                        "Delete " + friend.getName(),
                        "Are you sure you want to delete " + friend.getName() + "?",
                        "Delete",
                        "Cancel",
                        () -> {
                            viewModel.deleteFriend(friend);
                            Toast.makeText(requireContext(), "Delete " + friend.getName(), Toast.LENGTH_SHORT).show();
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
            UiState.Empty<List<Friend>> empty = (UiState.Empty<List<Friend>>) state;
            showState(empty.getMessage(), false);
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
            UiState.Empty<List<Group>> empty = (UiState.Empty<List<Group>>) state;
            showState(empty.getMessage(), false);
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
    }

    private void showState(String message, boolean showRetry) {
        recyclerView.setVisibility(View.GONE);
        progressLoading.setVisibility(View.GONE);
        stateLayout.setVisibility(View.VISIBLE);
        tvStateMessage.setText(message);
        btnRetry.setVisibility(showRetry ? View.VISIBLE : View.GONE);
    }

    private void showList(RecyclerView.Adapter<?> adapter) {
        progressLoading.setVisibility(View.GONE);
        stateLayout.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        recyclerView.setAdapter(adapter);
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
                        int color = getResources().getColor(com.bif.app.core.R.color.avatar_purple, null);
                        viewModel.addFriend(inputText, inputText.substring(0, 1).toUpperCase(), color);
                        Toast.makeText(requireContext(), "Added friend: " + inputText, Toast.LENGTH_SHORT).show();
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
