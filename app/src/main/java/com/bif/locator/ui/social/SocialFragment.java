package com.bif.locator.ui.social;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bif.locator.R;
import com.bif.locator.domain.model.Friend;
import com.bif.locator.domain.model.Group;
import com.google.android.material.tabs.TabLayout;

import java.util.Arrays;
import java.util.List;

public class SocialFragment extends Fragment {

    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private FriendsAdapter friendsAdapter;
    private GroupsAdapter groupsAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_social, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout);
        recyclerView = view.findViewById(R.id.recycler_view);

        setupRecyclerView();
        setupTabs();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Setup Friends Adapter
        friendsAdapter = new FriendsAdapter(new FriendsAdapter.OnFriendActionListener() {
            @Override
            public void onAddFriendClick() {
                Toast.makeText(requireContext(), "Add New Friend", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteFriendClick(Friend friend, int position) {
                Toast.makeText(requireContext(), "Delete " + friend.getName(), Toast.LENGTH_SHORT).show();
            }
        });

        // Setup Groups Adapter
        groupsAdapter = new GroupsAdapter(new GroupsAdapter.OnGroupActionListener() {
            @Override
            public void onCreateGroupClick() {
                Toast.makeText(requireContext(), "Create New Group", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onGroupOptionsClick(Group group, int position) {
                Toast.makeText(requireContext(), group.getName() + " options", Toast.LENGTH_SHORT).show();
            }
        });

        // Set initial data and adapter (Friends)
        friendsAdapter.setFriends(getSampleFriends());
        recyclerView.setAdapter(friendsAdapter);
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    // Friends Tab
                    friendsAdapter.setFriends(getSampleFriends());
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

    private List<Friend> getSampleFriends() {
        return Arrays.asList(
                new Friend("Alice Nguyen", "A", getResources().getColor(R.color.avatar_red, null), false),
                new Friend("Bob Tran", "B", getResources().getColor(R.color.avatar_blue, null), false),
                new Friend("Charlie Le", "C", getResources().getColor(R.color.avatar_green, null), false)
        );
    }

    private List<Group> getSampleGroups() {
        return Arrays.asList(
                new Group("Family", "F", getResources().getColor(R.color.avatar_purple, null), 4),
                new Group("High School Friends", "H", getResources().getColor(R.color.avatar_yellow, null), 8),
                new Group("Work Team", "W", getResources().getColor(R.color.avatar_blue, null), 6)
        );
    }
}
