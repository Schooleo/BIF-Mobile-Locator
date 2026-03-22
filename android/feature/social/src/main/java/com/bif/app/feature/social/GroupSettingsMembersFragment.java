package com.bif.app.feature.social;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GroupSettingsMembersFragment extends Fragment {

    private GroupDetailViewModel viewModel;
    private GroupSettingsMembersAdapter membersAdapter;
    private String groupId;

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

        btnBack.setOnClickListener(v -> navigateBackToGroupChat(view));
        btnAdd.setOnClickListener(v -> Toast.makeText(requireContext(), R.string.member_add_placeholder, Toast.LENGTH_SHORT).show());

        setupTabs(tabLayout, 2, view);

        membersAdapter = new GroupSettingsMembersAdapter(this::confirmRemoveMember);
        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMembers.setAdapter(membersAdapter);

        viewModel.loadGroup(groupId);
        viewModel.getGroup().observe(getViewLifecycleOwner(), group -> {
            if (group == null) {
                return;
            }
            tvAvatar.setText(group.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(group.getAvatarColor()));
            tvName.setText(group.getName());
            tvMemberCount.setText(getString(R.string.chat_member_count, group.getMemberCount()));
            membersAdapter.submit(group.getMembers(), group.isOwner());
        });
    }

    private void confirmRemoveMember(Friend member) {
        DialogUtils.showConfirmDialog(requireContext(),
                getString(R.string.remove_member),
                getString(R.string.remove_member_confirm, member.getName()),
                getString(R.string.remove),
                getString(R.string.cancel),
                () -> {
                    viewModel.removeMember(member);
                    Toast.makeText(requireContext(),
                            getString(R.string.member_removed, member.getName()),
                            Toast.LENGTH_SHORT).show();
                });
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

}
