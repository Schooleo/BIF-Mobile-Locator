package com.bif.app.feature.auth;

import android.os.Bundle;
import android.os.CountDownTimer;
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

import android.net.Uri;
import com.bif.app.core.utils.UriUtils;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ForgotPasswordOtpFragment extends Fragment {

    private ForgotPasswordViewModel viewModel;
    private CountDownTimer resendTimer;
    private String email = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forgot_password_otp, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ForgotPasswordViewModel.class);
        NavController navController = Navigation.findNavController(view);

        // --- Back button ---
        view.findViewById(R.id.iv_back).setOnClickListener(v -> navController.popBackStack());

        // --- Receive email from arguments ---
        Bundle args = getArguments();
        if (args != null) {
            email = args.getString("email", "").trim();
        }

        TextView tvOtpEmail = view.findViewById(R.id.tv_otp_email);
        if (!email.isEmpty()) {
            tvOtpEmail.setText(email);
        }

        // --- OTP input ---
        EditText etOtp = view.findViewById(R.id.et_otp_input);

        // --- Verify button ---
        Button btnVerify = view.findViewById(R.id.btn_verify_otp);
        btnVerify.setText(R.string.verify_code);
        btnVerify.setEnabled(false);

        // Enable verify button only when 6 digits entered
        etOtp.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean isFull = s.toString().trim().length() == 6;
                btnVerify.setEnabled(isFull);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnVerify.setOnClickListener(v -> {
            String otp = etOtp.getText().toString().trim();
            viewModel.verifyOtp(email, otp);
        });

        // --- Resend OTP ---
        TextView tvResend = view.findViewById(R.id.tv_resend_otp);
        tvResend.setOnClickListener(v -> {
            viewModel.requestOtp(email);
            startResendCountdown(tvResend);
        });

        // Start countdown immediately on entry
        startResendCountdown(tvResend);

        // --- Observe states ---
        observeVerifyOtpState(navController, btnVerify, etOtp);
        observeRequestOtpState();
    }

    private void observeVerifyOtpState(NavController navController, Button button, EditText etOtp) {
        viewModel.getVerifyOtpState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof ForgotPasswordViewModel.UiState.Loading) {
                setVerifyLoading(button, true, etOtp);
                return;
            }

            setVerifyLoading(button, false, etOtp);

            if (state instanceof ForgotPasswordViewModel.UiState.Success) {
                String resetToken = viewModel.getResetToken();
                Uri resetUri = UriUtils.buildUri("/forgot-password/reset")
                        .buildUpon()
                        .appendQueryParameter("email", email)
                        .appendQueryParameter("resetToken", resetToken)
                        .build();
                navController.navigate(resetUri);
                viewModel.clearVerifyOtpState();
                return;
            }

            if (state instanceof ForgotPasswordViewModel.UiState.Error) {
                String message = ((ForgotPasswordViewModel.UiState.Error) state).getMessage();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                viewModel.clearVerifyOtpState();
            }
        });
    }

    private void observeRequestOtpState() {
        viewModel.getRequestOtpState().observe(getViewLifecycleOwner(), state -> {
            if (state instanceof ForgotPasswordViewModel.UiState.Success) {
                Toast.makeText(requireContext(), R.string.otp_resent, Toast.LENGTH_SHORT).show();
                viewModel.clearRequestOtpState();
            } else if (state instanceof ForgotPasswordViewModel.UiState.Error) {
                String message = ((ForgotPasswordViewModel.UiState.Error) state).getMessage();
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                viewModel.clearRequestOtpState();
            }
        });
    }

    private void setVerifyLoading(Button button, boolean isLoading, EditText etOtp) {
        if (isLoading) {
            button.setEnabled(false);
        } else {
            boolean isFull = etOtp.getText().toString().trim().length() == 6;
            button.setEnabled(isFull);
        }
    }

    private void startResendCountdown(TextView tvResend) {
        tvResend.setEnabled(false);

        if (resendTimer != null) {
            resendTimer.cancel();
        }

        resendTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvResend.setText(getString(R.string.resend_otp_countdown, seconds));
                tvResend.setAlpha(0.5f);
            }

            @Override
            public void onFinish() {
                tvResend.setText(R.string.resend_otp);
                tvResend.setEnabled(true);
                tvResend.setAlpha(1.0f);
            }
        }.start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (resendTimer != null) {
            resendTimer.cancel();
            resendTimer = null;
        }
    }
}