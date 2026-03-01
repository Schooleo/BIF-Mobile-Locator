package com.bif.app.feature.auth;

import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;

public class RegisterFragment extends Fragment {

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

        NavController navController = Navigation.findNavController(view);

        Uri loginUri = UriUtils.buildUri("/login");

        // Set button text to "Sign up"
        Button btnSignUp = view.findViewById(R.id.btn_signup);
        btnSignUp.setText(R.string.sign_up);
        btnSignUp.setOnClickListener(v -> {
            // TODO: Add registration logic here
            // For now, just navigate back to login
            navController.navigate(loginUri);
        });

        // Set link text to "Already have an account?"
        TextView tvSignInLink = view.findViewById(R.id.tv_signin_link);
        tvSignInLink.setText(R.string.already_have_account);
        tvSignInLink.setPaintFlags(tvSignInLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvSignInLink.setOnClickListener(v -> navController.navigate(loginUri));
    }
}
