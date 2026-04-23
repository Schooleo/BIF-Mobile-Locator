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
        btnSendOtp.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                etEmail.setError(getString(R.string.email_required));
                etEmail.requestFocus();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError(getString(R.string.invalid_email_format));
                etEmail.requestFocus();
                return;
            }

            viewModel.requestOtp(email);
        });

        TextView tvBackToLogin = view.findViewById(R.id.tv_back_login_link);
        tvBackToLogin.setText(R.string.back_to_login);
        tvBackToLogin.setPaintFlags(tvBackToLogin.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvBackToLogin.setOnClickListener(v -> navController.navigate(UriUtils.buildUri("/login")));

        observeRequestOtpState(navController, btnSendOtp);
    }

    private void observeRequestOtpState(NavController navController, Button button) {
        viewModel.getRequestOtpState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof ForgotPasswordViewModel.RequestOtpState.Loading) {
                setLoading(button, true);
                return;
            }

            setLoading(button, false);

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

    private void setLoading(Button button, boolean isLoading) {
        button.setEnabled(!isLoading);
        button.setAlpha(isLoading ? 0.7f : 1f);
    }
}