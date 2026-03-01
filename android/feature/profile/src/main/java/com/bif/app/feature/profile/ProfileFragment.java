package com.bif.app.feature.profile;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    private NavController navController;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        setupHeader(view);
        setupSections(view);
        setupMenuItems(view);
        setupDarkModeToggle(view);
        setupLogout(view);
    }

    private void setupHeader(View view) {
        // Setup avatar with initial
        TextView tvAvatar = view.findViewById(com.bif.app.core.R.id.tvAvatar);
        tvAvatar.setText("B");
        tvAvatar.setBackgroundTintList(ColorStateList.valueOf(0xFF2B7FFF));

        // Edit profile button
        view.findViewById(com.bif.app.core.R.id.btnEditProfile).setOnClickListener(v -> {
            // TODO: Navigate to edit profile screen
        });
    }

    private void setupSections(View view) {
        TextView sectionAppSettings = view.findViewById(R.id.sectionAppSettings);
        sectionAppSettings.setText(R.string.app_settings);

        TextView sectionAccount = view.findViewById(R.id.sectionAccount);
        sectionAccount.setText(R.string.account);
    }

    private void setupMenuItems(View view) {
        // Personal Information
        View menuPersonalInfo = view.findViewById(R.id.menuPersonalInfo);
        ImageView iconPersonalInfo = menuPersonalInfo.findViewById(com.bif.app.core.R.id.ivIcon);
        TextView titlePersonalInfo = menuPersonalInfo.findViewById(com.bif.app.core.R.id.tvTitle);
        iconPersonalInfo.setImageResource(com.bif.app.core.R.drawable.ic_person);
        titlePersonalInfo.setText(R.string.personal_information);
        menuPersonalInfo.setOnClickListener(v -> {
            // TODO: Navigate to personal information screen
        });

        // Privacy & Security
        View menuPrivacySecurity = view.findViewById(R.id.menuPrivacySecurity);
        ImageView iconPrivacySecurity = menuPrivacySecurity.findViewById(com.bif.app.core.R.id.ivIcon);
        TextView titlePrivacySecurity = menuPrivacySecurity.findViewById(com.bif.app.core.R.id.tvTitle);
        iconPrivacySecurity.setImageResource(com.bif.app.core.R.drawable.ic_security);
        titlePrivacySecurity.setText(R.string.privacy_security);
        menuPrivacySecurity.setOnClickListener(v -> {
            // TODO: Navigate to privacy & security screen
        });
    }

    private void setupDarkModeToggle(View view) {
        View menuDarkMode = view.findViewById(R.id.menuDarkMode);
        ImageView iconDarkMode = menuDarkMode.findViewById(com.bif.app.core.R.id.ivIcon);
        TextView titleDarkMode = menuDarkMode.findViewById(com.bif.app.core.R.id.tvTitle);
        SwitchCompat switchDarkMode = menuDarkMode.findViewById(com.bif.app.core.R.id.switchToggle);

        iconDarkMode.setImageResource(com.bif.app.core.R.drawable.ic_dark_mode);
        titleDarkMode.setText(R.string.dark_mode);
        switchDarkMode.setVisibility(View.VISIBLE);

        // Get current theme mode
        int currentNightMode = AppCompatDelegate.getDefaultNightMode();
        boolean isDarkMode = currentNightMode == AppCompatDelegate.MODE_NIGHT_YES;
        switchDarkMode.setChecked(isDarkMode);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }

    private void setupLogout(View view) {
        view.findViewById(R.id.btnLogout).setOnClickListener(v -> {
            // Clear user data and navigate to login
            navController.navigate(UriUtils.buildUri(UriUtils.PathTo.LOGIN));
        });
    }
}
