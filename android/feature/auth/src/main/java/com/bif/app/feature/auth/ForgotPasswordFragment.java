package com.bif.app.feature.auth;

import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
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
public class ForgotPasswordFragment extends Fragment {

    private ForgotPasswordViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forgot_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);
        NavController navController = Navigation.findNavController(view);

        EditText etEmail = view.findViewById(com.bif.app.core.R.id.et_input);

        Button btnSendOtp = view.findViewById(R.id.btn_send_otp);
        btnSendOtp.setText(R.string.send_otp);
        btnSendOtp.setEnabled(false);

        etEmail.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String email = s.toString().trim();
                boolean isValid = !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
                btnSendOtp.setEnabled(isValid);
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnSendOtp.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            // TODO: restore viewModel.requestOtp(email) when API is ready
            Uri otpUri = UriUtils.buildUri("/forgot-password/otp")
                    .buildUpon()
                    .appendQueryParameter("email", email)
                    .build();
            navController.navigate(otpUri);
        });

        TextView tvBackToLogin = view.findViewById(R.id.tv_back_login_link);
        tvBackToLogin.setText(R.string.back_to_login);
        tvBackToLogin.setPaintFlags(tvBackToLogin.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvBackToLogin.setOnClickListener(v -> navController.navigate(UriUtils.buildUri("/login")));

        observeRequestOtpState(navController, btnSendOtp, etEmail);
    }

    private void observeRequestOtpState(NavController navController, Button button, EditText etEmail) {
        viewModel.getRequestOtpState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof ForgotPasswordViewModel.RequestOtpState.Loading) {
                setLoading(button, true, etEmail);
                return;
            }

            setLoading(button, false, etEmail);

            if (state instanceof ForgotPasswordViewModel.RequestOtpState.Success) {
                String email = ((ForgotPasswordViewModel.RequestOtpState.Success) state).getEmail();
                Uri otpUri = UriUtils.buildUri("/forgot-password/otp")
                        .buildUpon()
                        .appendQueryParameter("email", email)
                        .build();
                navController.navigate(otpUri);
                viewModel.clearTransientState();
                return;
            }

            if (state instanceof ForgotPasswordViewModel.RequestOtpState.Error) {
                String message = ((ForgotPasswordViewModel.RequestOtpState.Error) state).getMessage();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                viewModel.clearTransientState();
            }
        });
    }

    private void setLoading(Button button, boolean isLoading, EditText etEmail) {
        if (isLoading) {
            button.setEnabled(false);
        } else {
            String email = etEmail.getText().toString().trim();
            boolean isValid = !email.isEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches();
            button.setEnabled(isValid);
        }
    }
}