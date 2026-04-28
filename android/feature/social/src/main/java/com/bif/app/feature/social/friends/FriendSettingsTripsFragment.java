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
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;
import com.google.android.material.tabs.TabLayout;

public class FriendSettingsTripsFragment extends Fragment {

    private String friendId;
    private String friendName;
    private String avatarLetter;
    private int avatarColor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_friend_settings_trips, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        friendId = args != null ? args.getString("friendId", "") : "";
        friendName = args != null ? args.getString("friendName", getString(R.string.chat_friend_name)) : getString(R.string.chat_friend_name);
        avatarLetter = args != null ? args.getString("avatarLetter", "A") : "A";
        avatarColor = args != null ? args.getInt("avatarColor", 0) : 0;

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        TextView tvAvatar = view.findViewById(R.id.tv_friend_avatar);
        TextView tvName = view.findViewById(R.id.tv_friend_name);
        TabLayout tabLayout = view.findViewById(R.id.tab_friend_settings);

        tvAvatar.setText(avatarLetter);
        if (avatarColor != 0) {
            tvAvatar.setBackgroundTintList(ColorStateList.valueOf(avatarColor));
        }
        tvName.setText(friendName);

        btnBack.setOnClickListener(v -> navigateBackToFriendChat(view));
        setupTabs(tabLayout, 1, view);
    }

    private void setupTabs(TabLayout tabLayout, int selectedPosition, View rootView) {
        TabLayout.Tab selected = tabLayout.getTabAt(selectedPosition);
        if (selected != null) {
            selected.select();
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == selectedPosition) {
                    return;
                }
                android.net.Uri uri = UriUtils.buildUri(UriUtils.PathTo.FRIEND_SETTINGS_LOCATIONS)
                        .buildUpon()
                        .appendQueryParameter("friendId", friendId)
                        .appendQueryParameter("friendName", friendName)
                        .appendQueryParameter("avatarLetter", avatarLetter)
                        .appendQueryParameter("avatarColor", String.valueOf(avatarColor))
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

    private void navigateBackToFriendChat(View rootView) {
        android.net.Uri uri = UriUtils.buildUri(UriUtils.PathTo.SOCIAL_CHAT)
                .buildUpon()
                .appendQueryParameter("chatType", "friend")
                .appendQueryParameter("chatId", friendId)
                .appendQueryParameter("chatName", friendName)
                .appendQueryParameter("avatarLetter", avatarLetter)
                .appendQueryParameter("avatarColor", String.valueOf(avatarColor))
                .appendQueryParameter("memberCount", "0")
                .build();
        Navigation.findNavController(rootView).navigate(uri);
    }

}
