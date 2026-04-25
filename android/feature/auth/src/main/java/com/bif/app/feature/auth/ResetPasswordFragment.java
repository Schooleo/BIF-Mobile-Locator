package com.bif.app.feature.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

    private static final int MIN_PASSWORD_LENGTH = 6;

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
        btnReset.setEnabled(false);

        // --- Validation: enable button only when both fields valid ---
        TextWatcher validationWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String password = etPassword.getText().toString();
                String confirm = etConfirmPassword.getText().toString();
                boolean isValid = password.length() >= MIN_PASSWORD_LENGTH
                        && password.equals(confirm);
                btnReset.setEnabled(isValid);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etPassword.addTextChangedListener(validationWatcher);
        etConfirmPassword.addTextChangedListener(validationWatcher);

        // --- Reset click ---
        btnReset.setOnClickListener(v -> {
            String password = etPassword.getText().toString();
            String confirm = etConfirmPassword.getText().toString();

            if (password.length() < MIN_PASSWORD_LENGTH) {
                Toast.makeText(requireContext(), R.string.password_too_short, Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirm)) {
                Toast.makeText(requireContext(), R.string.passwords_do_not_match, Toast.LENGTH_SHORT).show();
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
            if (state instanceof ForgotPasswordViewModel.ResetPasswordState.Loading) {
                setLoading(button, true, etPassword, etConfirmPassword);
                return;
            }

            setLoading(button, false, etPassword, etConfirmPassword);

            if (state instanceof ForgotPasswordViewModel.ResetPasswordState.Success) {
                Toast.makeText(requireContext(), R.string.password_reset_success, Toast.LENGTH_SHORT).show();
                navController.navigate(UriUtils.buildUri("/login"));
                viewModel.clearResetPasswordState();
                return;
            }

            if (state instanceof ForgotPasswordViewModel.ResetPasswordState.Error) {
                String message = ((ForgotPasswordViewModel.ResetPasswordState.Error) state).getMessage();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                viewModel.clearResetPasswordState();
            }
        });
    }

    private void setLoading(Button button, boolean isLoading,
                            EditText etPassword, EditText etConfirmPassword) {
        if (isLoading) {
            button.setEnabled(false);
        } else {
            String password = etPassword.getText().toString();
            String confirm = etConfirmPassword.getText().toString();
            boolean isValid = password.length() >= MIN_PASSWORD_LENGTH
                    && password.equals(confirm);
            button.setEnabled(isValid);
        }
    }
}
