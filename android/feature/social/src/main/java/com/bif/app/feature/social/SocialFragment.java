package com.bif.app.feature.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
                navigateToGroupDetail(group);
            }

            @Override
            public void onGroupOptionsClick(Group group, int position) {
                handleGroupOptions(group);
            }
        });

        recyclerView.setAdapter(friendsAdapter);
    }

    private void observeViewModel() {
        viewModel.getFriends().observe(getViewLifecycleOwner(), friends -> {
            if (tabLayout.getSelectedTabPosition() == 0) {
                friendsAdapter.setFriends(friends);
            }
        });

        viewModel.getGroups().observe(getViewLifecycleOwner(), groups -> {
            if (tabLayout.getSelectedTabPosition() == 1) {
                groupsAdapter.setGroups(groups);
            }
        });
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // Friends Tab
                    friendsAdapter.setFriends(viewModel.getFriends().getValue());
                    recyclerView.setAdapter(friendsAdapter);
                } else {
                    // Groups Tab
                    groupsAdapter.setGroups(viewModel.getGroups().getValue());
                    recyclerView.setAdapter(groupsAdapter);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
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

    private void navigateToGroupDetail(Group group) {
        android.net.Uri destUri = UriUtils.buildUri(UriUtils.PathTo.GROUP_DETAIL).buildUpon()
                .appendQueryParameter("groupId", group.getServerId())
                .build();
        Navigation.findNavController(requireView()).navigate(destUri);
    }
}
