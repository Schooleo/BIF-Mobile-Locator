package com.bif.app.feature.profile;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.auth.AuthSessionManager;
import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bif.app.core.utils.UserPreferences;
import com.google.android.material.button.MaterialButton;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    private NavController navController;

    @Inject
    AuthSessionManager authSessionManager;

    private final ActivityResultLauncher<String> pickAvatarLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || !isAdded() || getView() == null) {
                    return;
                }
                UserPreferences.setAvatarUri(requireContext(), uri.toString());
                bindProfileState(getView());
                Toast.makeText(requireContext(), R.string.avatar_updated, Toast.LENGTH_SHORT).show();
            });

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
        String avatarUri = UserPreferences.getAvatarUri(requireContext());

        TextView tvAvatar = view.findViewById(com.bif.app.core.R.id.tvAvatar);
        tvAvatar.setBackgroundTintList(ColorStateList.valueOf(0xFF2B7FFF));
        ImageView ivAvatarImage = view.findViewById(com.bif.app.core.R.id.ivAvatarImage);
        View btnAvatarCamera = view.findViewById(com.bif.app.core.R.id.btnAvatarCamera);

        TextView tvName = view.findViewById(com.bif.app.core.R.id.tvName);
        TextView tvEmail = view.findViewById(com.bif.app.core.R.id.tvEmail);
        MaterialButton btnEditProfile = view.findViewById(com.bif.app.core.R.id.btnEditProfile);
        applyEditProfileButtonTint(btnEditProfile);

        View sectionAccount = view.findViewById(R.id.sectionAccount);
        View menuPersonalInfoView = view.findViewById(R.id.menuPersonalInfo);
        View menuPrivacySecurity = view.findViewById(R.id.menuPrivacySecurity);
        View logoutButton = view.findViewById(R.id.btnLogout);

        if (isLoggedIn) {
            btnAvatarCamera.setVisibility(View.VISIBLE);
            btnAvatarCamera.setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));

            tvAvatar.setText(resolveAvatarInitial(username, email));
            tvName.setText(username);
            tvEmail.setVisibility(View.VISIBLE);
            tvEmail.setText(email);
            bindAvatar(ivAvatarImage, tvAvatar, avatarUri);

            btnEditProfile.setText(R.string.edit_profile);
            btnEditProfile.setEnabled(true);
            btnEditProfile.setClickable(true);
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog(username));
            applyLoggedInButtonStyle(btnEditProfile);

            sectionAccount.setVisibility(View.VISIBLE);
            menuPersonalInfoView.setVisibility(View.VISIBLE);
            menuPrivacySecurity.setVisibility(View.VISIBLE);
            logoutButton.setVisibility(View.VISIBLE);
            return;
        }

        tvAvatar.setText("G");
        tvName.setText(R.string.guest_status);
        tvEmail.setVisibility(View.GONE);
        ivAvatarImage.setImageDrawable(null);
        ivAvatarImage.setVisibility(View.GONE);
        tvAvatar.setVisibility(View.VISIBLE);
        btnAvatarCamera.setVisibility(View.GONE);

        btnEditProfile.setText(R.string.log_in);
        btnEditProfile.setEnabled(true);
        btnEditProfile.setClickable(true);
        applyGuestLoginButtonStyle(btnEditProfile);
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
            DialogUtils.showConfirmDialog(requireContext(),
                "Logout",
                "Are you sure you want to logout?",
                "Logout",
                "Cancel",
                ()-> {
                    authSessionManager.logout(remoteSuccess -> {
                        if (!isAdded()) {
                            return;
                        }
                        Toast.makeText(requireContext(), R.string.logout_success, Toast.LENGTH_SHORT).show();
                        navController.navigate(UriUtils.buildUri(UriUtils.PathTo.LOGIN));
                    });
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

    private void bindAvatar(ImageView ivAvatarImage, TextView tvAvatar, String avatarUriString) {
        if (avatarUriString == null || avatarUriString.trim().isEmpty()) {
            ivAvatarImage.setImageDrawable(null);
            ivAvatarImage.setVisibility(View.GONE);
            tvAvatar.setVisibility(View.VISIBLE);
            return;
        }

        try {
            ivAvatarImage.setImageURI(Uri.parse(avatarUriString));
            if (ivAvatarImage.getDrawable() != null) {
                ivAvatarImage.setVisibility(View.VISIBLE);
                tvAvatar.setVisibility(View.INVISIBLE);
                return;
            }
        } catch (Exception ignored) {
            // Fall back to initial avatar when image cannot be resolved.
        }

        ivAvatarImage.setImageDrawable(null);
        ivAvatarImage.setVisibility(View.GONE);
        tvAvatar.setVisibility(View.VISIBLE);
    }

    private void showEditProfileDialog(String currentUsername) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.edit_profile_hint);
        if (!currentUsername.equals(getString(R.string.not_available))) {
            input.setText(currentUsername);
            input.setSelection(currentUsername.length());
        }

        int horizontalPadding = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        input.setPadding(horizontalPadding, input.getPaddingTop(), horizontalPadding, input.getPaddingBottom());

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.edit_profile_dialog_title)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String updatedUsername = input.getText().toString().trim();
                    if (updatedUsername.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.username_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    UserPreferences.setUsername(requireContext(), updatedUsername);
                    if (getView() != null) {
                        bindProfileState(getView());
                    }
                    Toast.makeText(requireContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyEditProfileButtonTint(MaterialButton button) {
        boolean isDarkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        if (!isDarkMode) {
            int lightGray = ContextCompat.getColor(requireContext(), com.bif.app.core.R.color.light_gray);
            button.setBackgroundTintList(ColorStateList.valueOf(lightGray));
            button.setTextColor(ContextCompat.getColor(requireContext(), com.bif.app.core.R.color.black));
            return;
        }

        TypedValue typedValue = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(com.bif.app.core.R.attr.colorListItemBackground,
                typedValue, true)) {
            int tintColor = typedValue.resourceId != 0
                    ? ContextCompat.getColor(requireContext(), typedValue.resourceId)
                    : typedValue.data;
            button.setBackgroundTintList(ColorStateList.valueOf(tintColor));
            button.setTextColor(ContextCompat.getColor(requireContext(), com.bif.app.core.R.color.white));
        }
    }

    private void applyLoggedInButtonStyle(MaterialButton button) {
        ViewGroup.LayoutParams baseParams = button.getLayoutParams();
        if (baseParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) baseParams;
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER_HORIZONTAL;
            params.leftMargin = 0;
            params.rightMargin = 0;
            button.setLayoutParams(params);
        }
        applyEditProfileButtonTint(button);
    }

    private void applyGuestLoginButtonStyle(MaterialButton button) {
        ViewGroup.LayoutParams baseParams = button.getLayoutParams();
        if (baseParams instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) baseParams;
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = (int) (52 * requireContext().getResources().getDisplayMetrics().density);
            params.height = height;
            params.gravity = Gravity.CENTER_HORIZONTAL;
            int horizontalMargin = (int) (24 * requireContext().getResources().getDisplayMetrics().density);
            params.leftMargin = horizontalMargin;
            params.rightMargin = horizontalMargin;
            button.setLayoutParams(params);
        }
        int green = ContextCompat.getColor(requireContext(), com.bif.app.core.R.color.primary_green);
        button.setBackgroundTintList(ColorStateList.valueOf(green));
        button.setTextColor(ContextCompat.getColor(requireContext(), com.bif.app.core.R.color.white));
        button.setTextSize(16f);
        int verticalPadding = (int) (12 * requireContext().getResources().getDisplayMetrics().density);
        button.setPadding(button.getPaddingLeft(), verticalPadding, button.getPaddingRight(), verticalPadding);
    }
}
