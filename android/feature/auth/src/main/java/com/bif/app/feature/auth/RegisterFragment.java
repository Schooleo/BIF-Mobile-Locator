package com.bif.app.feature.auth;

import android.graphics.Paint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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
public class RegisterFragment extends Fragment {

    private RegisterViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(RegisterViewModel.class);
        NavController navController = Navigation.findNavController(view);

        EditText etEmail = view.findViewById(com.bif.app.core.R.id.et_input);
        EditText etOtp = view.findViewById(R.id.et_otp);
        EditText etUsername = view.findViewById(com.bif.app.core.R.id.et_username);
        EditText etPassword = view.findViewById(com.bif.app.core.R.id.et_password);
        EditText etConfirmPassword = view.findViewById(com.bif.app.core.R.id.et_confirm_password);

        Button btnSendOtp = view.findViewById(R.id.btn_send_otp);
        Button btnRegister = view.findViewById(R.id.btn_register);

        btnRegister.setText(R.string.sign_up);
        btnSendOtp.setText(R.string.send_otp);

        btnSendOtp.setOnClickListener(v -> viewModel.requestOtp(etEmail.getText().toString()));

        btnRegister.setOnClickListener(v -> viewModel.register(
            etEmail.getText().toString(),
            etUsername.getText().toString(),
            etPassword.getText().toString()));

        // Set link text to "Already have an account?"
        TextView tvSignInLink = view.findViewById(R.id.tv_signin_link);
        tvSignInLink.setText(R.string.already_have_account);
        tvSignInLink.setPaintFlags(tvSignInLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvSignInLink.setOnClickListener(v -> navController.navigate(UriUtils.buildUri("/login")));

        bindText(etEmail, viewModel::onEmailChanged);
        bindText(etOtp, viewModel::onOtpChanged);
        bindText(etUsername, viewModel::onUsernameChanged);
        bindText(etPassword, viewModel::onPasswordChanged);
        bindText(etConfirmPassword, viewModel::onConfirmPasswordChanged);

        viewModel.getSendOtpEnabled().observe(getViewLifecycleOwner(), enabled ->
                btnSendOtp.setEnabled(Boolean.TRUE.equals(enabled)));
        viewModel.getOtpEnabled().observe(getViewLifecycleOwner(), enabled ->
                etOtp.setEnabled(Boolean.TRUE.equals(enabled)));
        viewModel.getCredentialsEnabled().observe(getViewLifecycleOwner(), enabled -> {
            boolean value = Boolean.TRUE.equals(enabled);
            etUsername.setEnabled(value);
            etPassword.setEnabled(value);
            etConfirmPassword.setEnabled(value);
        });
        viewModel.getRegisterEnabled().observe(getViewLifecycleOwner(), enabled ->
                btnRegister.setEnabled(Boolean.TRUE.equals(enabled)));

        viewModel.getRequestOtpState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof RegisterViewModel.UiState.Loading) {
                btnSendOtp.setEnabled(false);
                return;
            }

            if (state instanceof RegisterViewModel.UiState.Success) {
                btnSendOtp.setEnabled(true);
                Toast.makeText(requireContext(), "OTP sent", Toast.LENGTH_SHORT).show();
                viewModel.onSendOtpClicked();
                return;
            }

            if (state instanceof RegisterViewModel.UiState.Error) {
                btnSendOtp.setEnabled(true);
                String message = ((RegisterViewModel.UiState.Error) state).getMessage();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getRegisterState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof RegisterViewModel.UiState.Loading) {
                btnRegister.setEnabled(false);
                return;
            }

            btnRegister.setEnabled(true);

            if (state instanceof RegisterViewModel.UiState.Success) {
                Toast.makeText(requireContext(), "Register success", Toast.LENGTH_SHORT).show();
                navController.navigate(UriUtils.buildUri("/login"));
                return;
            }

            if (state instanceof RegisterViewModel.UiState.Error) {
                String message = ((RegisterViewModel.UiState.Error) state).getMessage();
                if ("Invalid OTP".equalsIgnoreCase(message)) {
                    etOtp.setError("Invalid OTP");
                    etOtp.requestFocus();
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.onEmailChanged(etEmail.getText().toString());
        viewModel.onOtpChanged(etOtp.getText().toString());
        viewModel.onUsernameChanged(etUsername.getText().toString());
        viewModel.onPasswordChanged(etPassword.getText().toString());
        viewModel.onConfirmPasswordChanged(etConfirmPassword.getText().toString());
    }

    private void bindText(EditText editText, TextChangeListener listener) {
        editText.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                listener.onChanged(s == null ? "" : s.toString());
            }
        });
    }

    private interface TextChangeListener {
        void onChanged(String value);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
