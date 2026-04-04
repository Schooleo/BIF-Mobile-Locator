package com.bif.app.feature.profile;

import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bif.app.core.auth.AuthSessionManager;
import com.bif.app.core.utils.DialogUtils;
import com.bif.app.core.utils.UriUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.button.MaterialButton;

import javax.inject.Inject;

import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfileFragment extends Fragment {

    private NavController navController;
    private ProfileViewModel viewModel;
    private SwipeRefreshLayout swipeRefreshLayout;

    @Inject
    AuthSessionManager authSessionManager;

    private final ActivityResultLauncher<String> pickAvatarLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null || !isAdded() || getView() == null) {
                    return;
                }
                viewModel.onAvatarSelected(uri.toString());
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);

        observeViewModel(view);
        setupSwipeRefresh(view);
        setupSections(view);
        setupMenuItems(view);
        setupDarkModeToggle(view);
        setupLogout(view);
        viewModel.loadFromLocal();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadFromLocal();
        viewModel.refreshProfileFromServer(false);
    }

    private void observeViewModel(@NonNull View view) {
        viewModel.getProfileState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }
            bindProfileState(view, state);
        });

        viewModel.getMessageResId().observe(getViewLifecycleOwner(), messageId -> {
            if (messageId == null || !isAdded()) {
                return;
            }
            Toast.makeText(requireContext(), messageId, Toast.LENGTH_SHORT).show();
            viewModel.consumeMessage();
        });

        viewModel.getIsRefreshing().observe(getViewLifecycleOwner(), isRefreshing -> {
            if (swipeRefreshLayout == null) {
                return;
            }
            swipeRefreshLayout.setRefreshing(Boolean.TRUE.equals(isRefreshing));
        });
    }

    private void setupSwipeRefresh(@NonNull View view) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        if (swipeRefreshLayout == null) {
            return;
        }
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refreshProfileFromServer(true));
    }

    private void bindProfileState(View view, ProfileViewModel.ProfileUiState state) {

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

        if (state.isLoggedIn) {
            btnAvatarCamera.setVisibility(View.VISIBLE);
            btnAvatarCamera.setOnClickListener(v -> pickAvatarLauncher.launch("image/*"));

            tvAvatar.setText(state.avatarInitial);
            tvName.setText(state.usernameForDisplay);
            tvEmail.setVisibility(View.VISIBLE);
            tvEmail.setText(state.emailForDisplay);
            bindAvatar(ivAvatarImage, tvAvatar, state.avatarUri);

            btnEditProfile.setText(R.string.edit_profile);
            btnEditProfile.setEnabled(true);
            btnEditProfile.setClickable(true);
            btnEditProfile.setOnClickListener(v -> showEditProfileDialog(state.usernameForDisplay));
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
        ivAvatarImage.setImageURI(null);
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
        menuPersonalInfo
                .setOnClickListener(v -> navController.navigate(UriUtils.buildUri(UriUtils.PathTo.PERSONAL_INFO)));

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

        // Get current theme mode from actual configuration
        boolean isDarkMode = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
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
                    () -> {
                        authSessionManager.logout(remoteSuccess -> {
                            if (!isAdded()) {
                                return;
                            }

                            requireActivity().runOnUiThread(() -> {
                                if (!isAdded()) {
                                    return;
                                }
                                Toast.makeText(requireContext(), R.string.logout_success, Toast.LENGTH_SHORT).show();
                                navController.navigate(UriUtils.buildUri(UriUtils.PathTo.LOGIN));
                            });
                        });
                    });
        });
    }

    private void bindAvatar(ImageView ivAvatarImage, TextView tvAvatar, String avatarUriString) {
        if (avatarUriString == null || avatarUriString.trim().isEmpty()) {
            Glide.with(this).clear(ivAvatarImage);
            ivAvatarImage.setOnClickListener(null);
            ivAvatarImage.setContentDescription(null);
            ivAvatarImage.setImageDrawable(null);
            ivAvatarImage.setVisibility(View.GONE);
            tvAvatar.setVisibility(View.VISIBLE);
            return;
        }

        try {
            String trimmed = avatarUriString.trim();
            boolean isRemote = (trimmed.startsWith("http://")
                    || trimmed.startsWith("https://"));
            Object imageSource = isRemote
                    ? trimmed
                    : new File(trimmed);

            ivAvatarImage.setOnClickListener(null);
            ivAvatarImage.setContentDescription(null);

            Glide.with(this)
                    .load(imageSource)
                    .error(com.bif.app.core.R.drawable.bg_logo_placeholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e,
                                Object model,
                                Target<Drawable> target,
                                boolean isFirstResource) {
                            if (isRemote) {
                                String unavailableText = ivAvatarImage.getContext()
                                        .getString(R.string.image_unavailable_offline);
                                ivAvatarImage.setContentDescription(
                                        unavailableText);
                                ivAvatarImage.setOnClickListener(v -> {
                                    if (!isAdded()) {
                                        return;
                                    }
                                    Toast.makeText(v.getContext(),
                                            R.string.image_unavailable_offline,
                                            Toast.LENGTH_SHORT).show();
                                    viewModel.refreshProfileFromServer(true);
                                });
                            }
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                DataSource dataSource,
                                boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(ivAvatarImage);
            ivAvatarImage.setVisibility(View.VISIBLE);
            tvAvatar.setVisibility(View.INVISIBLE);
            return;
        } catch (Exception ignored) {
            // Fall back to initial avatar when image cannot be resolved.
        }

        ivAvatarImage.setImageDrawable(null);
        ivAvatarImage.setVisibility(View.GONE);
        tvAvatar.setVisibility(View.VISIBLE);
    }

    private void showEditProfileDialog(String currentUsername) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_edit_profile, null);

        EditText etUsername = dialogView.findViewById(R.id.et_username);
        Button btnSave = dialogView.findViewById(R.id.btn_save);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);

        if (!currentUsername.equals(getString(R.string.not_available))) {
            etUsername.setText(currentUsername);
            etUsername.setSelection(currentUsername.length());
        }

        // Khởi tạo Dialog với giao diện xịn từ nhánh dev
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Nút Save với logic gọi Backend API từ nhánh của bạn
        btnSave.setOnClickListener(v -> {
            String updatedUsername = etUsername.getText().toString().trim();
            if (updatedUsername.isEmpty()) {
                Toast.makeText(requireContext(), R.string.username_required, Toast.LENGTH_SHORT).show();
                return;
            }

            // Logic quan trọng của bạn: Đẩy lên Backend thông qua ViewModel
            viewModel.updateProfile(updatedUsername);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
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