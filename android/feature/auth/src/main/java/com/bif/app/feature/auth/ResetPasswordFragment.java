package com.bif.app.feature.auth;

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

import com.bif.app.core.utils.UriUtils;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ResetPasswordFragment extends Fragment {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private ForgotPasswordViewModel viewModel;
    private String email = "";
    private String resetToken = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reset_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);
        NavController navController = Navigation.findNavController(view);

        // --- Back button ---
        view.findViewById(R.id.iv_back).setOnClickListener(v -> navController.popBackStack());

        // --- Receive arguments ---
        Bundle args = getArguments();
        if (args != null) {
            email = args.getString("email", "").trim();
            resetToken = args.getString("resetToken", "").trim();
        }

        // --- Inputs ---
        EditText etPassword = view.findViewById(com.bif.app.core.R.id.et_password);
        EditText etConfirmPassword = view.findViewById(com.bif.app.core.R.id.et_confirm_password);

        etPassword.setHint(R.string.new_password_hint);
        etConfirmPassword.setHint(R.string.confirm_password_hint);

        // --- Reset button ---
        Button btnReset = view.findViewById(R.id.btn_reset_password);
        btnReset.setText(R.string.reset_password_button);

        // --- Reset click ---
        btnReset.setOnClickListener(v -> {
            String password = etPassword.getText().toString();
            String confirm = etConfirmPassword.getText().toString();

            if (password.length() < MIN_PASSWORD_LENGTH) {
                etPassword.setError(getString(R.string.password_too_short));
                etPassword.requestFocus();
                return;
            }

            if (!password.equals(confirm)) {
                etConfirmPassword.setError(getString(R.string.passwords_do_not_match));
                etConfirmPassword.requestFocus();
                return;
            }

            viewModel.resetPassword(resetToken, password);
        });

        // --- Observe state ---
        observeResetPasswordState(navController, btnReset, etPassword, etConfirmPassword);
    }

    private void observeResetPasswordState(NavController navController, Button button,
                                           EditText etPassword, EditText etConfirmPassword) {
        viewModel.getResetPasswordState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof ForgotPasswordViewModel.UiState.Loading) {
                setLoading(button, true, etPassword, etConfirmPassword);
                return;
            }

            setLoading(button, false, etPassword, etConfirmPassword);

            if (state instanceof ForgotPasswordViewModel.UiState.Success) {
                Toast.makeText(requireContext(), R.string.password_reset_success, Toast.LENGTH_SHORT).show();
                navController.navigate(UriUtils.buildUri("/login"));
                viewModel.clearResetPasswordState();
                return;
            }

            if (state instanceof ForgotPasswordViewModel.UiState.Error) {
                etPassword.setError(getString(R.string.password_reset_failed));
                etPassword.requestFocus();
                viewModel.clearResetPasswordState();
            }
        });
    }

    private void setLoading(Button button, boolean isLoading,
                            EditText etPassword, EditText etConfirmPassword) {
        button.setEnabled(!isLoading);
    }
}
