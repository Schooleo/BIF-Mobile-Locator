package com.bif.app.feature.auth;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;

public class ForgotPasswordOtpFragment extends Fragment {

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

        String email = "";
        Bundle args = getArguments();
        if (args != null) {
            email = args.getString("email", "").trim();
        }

        TextView tvOtpEmail = view.findViewById(R.id.tv_otp_email);
        if (!email.isEmpty()) {
            tvOtpEmail.setText(getString(R.string.otp_sent_to, email));
        }

        NavController navController = Navigation.findNavController(view);
        TextView tvBackToLogin = view.findViewById(R.id.tv_back_login_link);
        tvBackToLogin.setText(R.string.back_to_login);
        tvBackToLogin.setPaintFlags(tvBackToLogin.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvBackToLogin.setOnClickListener(v -> navController.navigate(UriUtils.buildUri("/login")));
    }
}