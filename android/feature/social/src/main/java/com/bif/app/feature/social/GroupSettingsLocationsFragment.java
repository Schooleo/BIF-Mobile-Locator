package com.bif.app.feature.social;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.domain.model.Group;
import com.google.android.material.tabs.TabLayout;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class GroupSettingsLocationsFragment extends Fragment {

    private GroupDetailViewModel viewModel;
    private String groupId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_group_settings_locations, container, false);
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
        TabLayout tabLayout = view.findViewById(R.id.tab_group_settings);

        btnBack.setOnClickListener(v -> navigateBackToGroupChat(view));

        setupTabs(tabLayout, 1, view);

        viewModel.loadGroup(groupId);
        viewModel.getGroup().observe(getViewLifecycleOwner(), group -> {
            if (group == null) {
                return;
            }
            tvAvatar.setText(group.getAvatarLetter());
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(group.getAvatarColor()));
            tvName.setText(group.getName());
            tvMemberCount.setText(getString(R.string.chat_member_count, group.getMemberCount()));
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
                        : UriUtils.PathTo.GROUP_SETTINGS_MEMBERS;
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
