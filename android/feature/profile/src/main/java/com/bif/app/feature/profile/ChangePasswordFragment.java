package com.bif.app.feature.profile;

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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.auth.AuthSessionManager;
import com.bif.app.core.network.RestApiService;
import com.bif.app.core.utils.UriUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ChangePasswordFragment extends Fragment {

    private ChangePasswordViewModel viewModel;

    @Inject
    RestApiService restApiService;

    @Inject
    AuthSessionManager authSessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_change_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ChangePasswordViewModel.class);
        NavController navController = Navigation.findNavController(view);

        view.findViewById(R.id.iv_back).setOnClickListener(v -> navController.popBackStack());

        EditText etCurrentPassword = view.findViewById(R.id.et_current_password);
        EditText etNewPassword = view.findViewById(R.id.et_new_password);
        EditText etConfirmPassword = view.findViewById(R.id.et_confirm_password);

        Button btnChangePassword = view.findViewById(R.id.btn_change_password);
        btnChangePassword.setText(R.string.profile_change_password);

        observeChangePasswordState(navController, btnChangePassword,
            etCurrentPassword, etNewPassword, etConfirmPassword);

        btnChangePassword.setOnClickListener(v -> {
            etCurrentPassword.setError(null);
            etNewPassword.setError(null);
            etConfirmPassword.setError(null);

            String currentPassword = etCurrentPassword.getText().toString();
            String newPassword = etNewPassword.getText().toString();
            String confirmPassword = etConfirmPassword.getText().toString();

            ChangePasswordViewModel.ValidationError validationError =
                    viewModel.validate(currentPassword, newPassword, confirmPassword);

            if (validationError == ChangePasswordViewModel.ValidationError.CURRENT_PASSWORD_EMPTY) {
                etCurrentPassword.setError(getString(R.string.profile_current_password_required));
                etCurrentPassword.requestFocus();
                return;
            }

            if (validationError == ChangePasswordViewModel.ValidationError.NEW_PASSWORD_TOO_SHORT) {
                etNewPassword.setError(getString(R.string.profile_password_too_short));
                etNewPassword.requestFocus();
                return;
            }

            if (validationError == ChangePasswordViewModel.ValidationError.CONFIRM_PASSWORD_MISMATCH) {
                etConfirmPassword.setError(getString(R.string.profile_passwords_do_not_match));
                etConfirmPassword.requestFocus();
                return;
            }

            viewModel.changePassword(restApiService, currentPassword, newPassword);
        });
    }

    private void observeChangePasswordState(NavController navController,
                                            Button btnChangePassword,
                                            EditText etCurrentPassword,
                                            EditText etNewPassword,
                                            EditText etConfirmPassword) {
        viewModel.getChangePasswordState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof ChangePasswordViewModel.UiState.Loading) {
                btnChangePassword.setEnabled(false);
                return;
            }

            btnChangePassword.setEnabled(true);

            if (state instanceof ChangePasswordViewModel.UiState.Success) {
                Toast.makeText(requireContext(), "Password changed successfully", Toast.LENGTH_SHORT).show();
                etCurrentPassword.setText("");
                etNewPassword.setText("");
                etConfirmPassword.setText("");
                viewModel.clearChangePasswordState();
                authSessionManager.clearSession(() -> {
                    if (!isAdded()) {
                        return;
                    }

                    requireActivity().runOnUiThread(() -> {
                        if (!isAdded()) {
                            return;
                        }
                        navController.navigate(UriUtils.buildUri(UriUtils.PathTo.LOGIN));
                    });
                });
                return;
            }

            if (state instanceof ChangePasswordViewModel.UiState.Error) {
                String message = ((ChangePasswordViewModel.UiState.Error) state).getMessage();

                String normalized = message == null ? "" : message.toLowerCase();
                if (normalized.contains("current password")) {
                    etCurrentPassword.setError("Current password is incorrect");
                    etCurrentPassword.requestFocus();
                } else {
                    etNewPassword.setError(message);
                    etNewPassword.requestFocus();
                }

                viewModel.clearChangePasswordState();
            }
        });
    }
}
