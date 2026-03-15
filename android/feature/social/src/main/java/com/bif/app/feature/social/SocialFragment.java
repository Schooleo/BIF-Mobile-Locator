package com.bif.app.feature.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.domain.model.Friend;
import com.bif.app.domain.model.Group;
import com.google.android.material.tabs.TabLayout;

import java.util.Arrays;
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
                showCreateGroupDialog();
            }

            @Override
            public void onGroupOptionsClick(Group group, int position) {
                Toast.makeText(requireContext(), group.getName() + " options", Toast.LENGTH_SHORT).show();
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
                    groupsAdapter.setGroups(getSampleGroups());
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

    private void showCreateGroupDialog() {
        DialogUtils.showCustomInputDialog(
                requireContext(),
                R.layout.dialog_create_group,
                R.id.btn_create_group,
                R.id.et_search,
                R.id.btn_close,
                inputText -> {
                    if (!inputText.isEmpty()) {
                        Toast.makeText(requireContext(), "Created group: " + inputText, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private List<Group> getSampleGroups() {
        return Arrays.asList(
                new Group("Family", "F", getResources().getColor(com.bif.app.core.R.color.avatar_purple, null), 4),
                new Group("High School Friends", "H", getResources().getColor(com.bif.app.core.R.color.avatar_yellow, null), 8),
                new Group("Work Team", "W", getResources().getColor(com.bif.app.core.R.color.avatar_blue, null), 6)
        );
    }
}
