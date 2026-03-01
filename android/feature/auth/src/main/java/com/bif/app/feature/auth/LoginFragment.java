package com.bif.app.feature.auth;

import android.graphics.Paint;
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

public class LoginFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        NavController navController = Navigation.findNavController(view);

        // Set button text to "Sign in"
        Button btnLogin = view.findViewById(R.id.btn_login);
        btnLogin.setText(R.string.sign_in);
        btnLogin.setOnClickListener(v -> {
            // TODO: Add authentication logic here
            // For now, just navigate to home for testing
            navController.navigate(UriUtils.buildUri());
        });

        // Set link text to "Register"
        TextView tvRegisterLink = view.findViewById(R.id.tv_register_link);
        tvRegisterLink.setText(R.string.register);
        tvRegisterLink.setPaintFlags(tvRegisterLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvRegisterLink.setOnClickListener(v -> navController.navigate(UriUtils.buildUri("/register")));
    }
}
