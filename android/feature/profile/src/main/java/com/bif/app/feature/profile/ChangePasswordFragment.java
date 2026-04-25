package com.bif.app.feature.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

public class ChangePasswordFragment extends Fragment {

    private ChangePasswordViewModel viewModel;

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
            }
        });
    }
}
