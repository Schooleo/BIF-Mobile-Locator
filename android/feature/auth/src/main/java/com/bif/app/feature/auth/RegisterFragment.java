package com.bif.app.feature.auth;

import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.utils.UriUtils;
import com.bif.app.core.utils.UserPreferences;

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

        EditText etUsername = view.findViewById(com.bif.app.core.R.id.et_username);
        EditText etEmail = view.findViewById(com.bif.app.core.R.id.et_input);
        EditText etPassword = view.findViewById(com.bif.app.core.R.id.et_password);
        EditText etConfirmPassword = view.findViewById(com.bif.app.core.R.id.et_confirm_password);

        // Set button text to "Sign up"
        Button btnSignUp = view.findViewById(R.id.btn_signup);
        btnSignUp.setText(R.string.sign_up);
        btnSignUp.setOnClickListener(v -> {
            // TODO: Add registration logic here
            String username = etUsername.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String confirmPassword = etConfirmPassword.getText().toString().trim();

            if (username.isEmpty()){
                etUsername.setError("Username is required");
                etUsername.requestFocus();
                return;
            }

            if (email.isEmpty()){
                etEmail.setError("Email is required");
                etEmail.requestFocus();
                return;
            }

            // Check xem email có đúng format không
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Please enter a valid email");
                etEmail.requestFocus();
                return;
            }

            if (password.isEmpty()){
                etPassword.setError("Password is required");
                etPassword.requestFocus();
                return;
            }

            if (password.length() < 6) {
                etPassword.setError("Password must be at least 6 characters");
                etPassword.requestFocus();
                return;
            }

            if (!password.equals(confirmPassword)) {
                etConfirmPassword.setError("Passwords do not match");
                etConfirmPassword.requestFocus();
                return;
            }

            UserPreferences.saveUser(getContext(), username, password, email);
            Toast.makeText(requireContext(), "Registration successful", Toast.LENGTH_SHORT).show();

            navController.navigate(loginUri);
        });

        // Set link text to "Already have an account?"
        TextView tvSignInLink = view.findViewById(R.id.tv_signin_link);
        tvSignInLink.setText(R.string.already_have_account);
        tvSignInLink.setPaintFlags(tvSignInLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvSignInLink.setOnClickListener(v -> navController.navigate(loginUri));
    }
}
