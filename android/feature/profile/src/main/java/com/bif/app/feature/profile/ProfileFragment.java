package com.bif.app.feature.profile;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.core.utils.UserPreferences;
import com.google.android.material.button.MaterialButton;

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

        bindProfileState(view);
        setupSections(view);
        setupMenuItems(view);
        setupDarkModeToggle(view);
        setupLogout(view);
    }

    private void bindProfileState(View view) {
        boolean isLoggedIn = UserPreferences.isLoggedIn(requireContext());
        String username = getStoredValue(UserPreferences.getUsername(requireContext()));
        String email = getStoredValue(UserPreferences.getEmail(requireContext()));

        TextView tvAvatar = view.findViewById(com.bif.app.core.R.id.tvAvatar);
        tvAvatar.setBackgroundTintList(ColorStateList.valueOf(0xFF2B7FFF));

        TextView tvName = view.findViewById(com.bif.app.core.R.id.tvName);
        TextView tvEmail = view.findViewById(com.bif.app.core.R.id.tvEmail);
        MaterialButton btnEditProfile = view.findViewById(com.bif.app.core.R.id.btnEditProfile);

        View sectionAccount = view.findViewById(R.id.sectionAccount);
        View menuPersonalInfoView = view.findViewById(R.id.menuPersonalInfo);
        View menuPrivacySecurity = view.findViewById(R.id.menuPrivacySecurity);
        View logoutButton = view.findViewById(R.id.btnLogout);

        if (isLoggedIn) {
            tvAvatar.setText(resolveAvatarInitial(username, email));
            tvName.setText(username);
            tvEmail.setText(email);

            btnEditProfile.setText(R.string.signed_in_badge);
            btnEditProfile.setEnabled(false);
            btnEditProfile.setClickable(false);

            sectionAccount.setVisibility(View.VISIBLE);
            menuPersonalInfoView.setVisibility(View.VISIBLE);
            menuPrivacySecurity.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.VISIBLE);
            return;
        }

        tvAvatar.setText(R.string.guest_status);
        tvName.setText(R.string.guest_profile_title);
        tvEmail.setText(R.string.guest_profile_subtitle);

        btnEditProfile.setText(R.string.log_in);
        btnEditProfile.setEnabled(true);
        btnEditProfile.setClickable(true);
        btnEditProfile.setOnClickListener(v -> navController.navigate(UriUtils.buildUri(UriUtils.PathTo.LOGIN)));

        sectionAccount.setVisibility(View.GONE);
        menuPersonalInfoView.setVisibility(View.GONE);
        menuPrivacySecurity.setVisibility(View.GONE);
        logoutButton.setVisibility(View.GONE);
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
        ((android.widget.ImageView) menuPersonalInfo.findViewById(com.bif.app.core.R.id.ivIcon))
            .setImageResource(com.bif.app.core.R.drawable.ic_person);
        ((android.widget.TextView) menuPersonalInfo.findViewById(com.bif.app.core.R.id.tvTitle))
            .setText(R.string.personal_information);
        menuPersonalInfo.setOnClickListener(v ->
            navController.navigate(UriUtils.buildUri(UriUtils.PathTo.PERSONAL_INFO)));

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

        titleDarkMode.setText(R.string.dark_mode);
        switchDarkMode.setVisibility(View.VISIBLE);

        // Get current theme mode
        int currentNightMode = AppCompatDelegate.getDefaultNightMode();
        boolean isDarkMode = currentNightMode == AppCompatDelegate.MODE_NIGHT_YES;
        switchDarkMode.setChecked(isDarkMode);
        iconDarkMode.setImageResource(isDarkMode
                ? com.bif.app.core.R.drawable.ic_moon
                : com.bif.app.core.R.drawable.ic_sun);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            iconDarkMode.setImageResource(isChecked
                    ? com.bif.app.core.R.drawable.ic_moon
                    : com.bif.app.core.R.drawable.ic_sun);
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
            DialogUtils.showConfirmDialog(requireContext(),
                "Logout",
                "Are you sure you want to logout?",
                "Logout",
                "Cancel",
                ()-> {
                    UserPreferences.clearUser(requireContext());
                    Toast.makeText(requireContext(), R.string.logout_success, Toast.LENGTH_SHORT).show();
                    navController.navigate(UriUtils.buildUri(UriUtils.PathTo.LOGIN));
                }
            );
        });
    }

    private String getStoredValue(String value) {
        if (value == null) {
            return getString(R.string.not_available);
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? getString(R.string.not_available) : trimmedValue;
    }

    private String resolveAvatarInitial(String username, String email) {
        if (!username.equals(getString(R.string.not_available))) {
            return username.substring(0, 1).toUpperCase();
        }
        if (!email.equals(getString(R.string.not_available))) {
            return email.substring(0, 1).toUpperCase();
        }
        return getString(R.string.guest_status);
    }
}
