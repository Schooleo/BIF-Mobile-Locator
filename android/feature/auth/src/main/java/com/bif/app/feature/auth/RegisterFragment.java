package com.bif.app.feature.auth;

import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.bif.app.core.utils.AppSnackbar;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bif.app.core.auth.AuthSessionManager;
import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.AuthResponse;
import com.bif.app.core.network.dto.auth.RegisterRequest;
import com.bif.app.core.utils.UriUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class RegisterFragment extends Fragment {

    private static final String TAG = "RegisterFragment";

    @Inject
    RestApiService restApiService;

    @Inject
    AuthSessionManager authSessionManager;

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

            if (password.length() < 8) {
                etPassword.setError("Password must be at least 8 characters");
                etPassword.requestFocus();
                return;
            }

            if (!password.equals(confirmPassword)) {
                etConfirmPassword.setError("Passwords do not match");
                etConfirmPassword.requestFocus();
                return;
            }

            setAuthLoading(btnSignUp, true);

            restApiService.register(new RegisterRequest(username, email, password, confirmPassword))
                    .enqueue(new Callback<>() {
                        @Override
                        public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                            setAuthLoading(btnSignUp, false);
                            if (!isAdded()) {
                                return;
                            }

                            if (response.isSuccessful() && response.body() != null) {
                                AuthResponse auth = response.body();
                                authSessionManager.saveSessionFromAuth(auth, username, email);

                                AppSnackbar.show(requireContext(), getString(R.string.registration_successful));
                                navController.navigate(UriUtils.buildUri(UriUtils.PathTo.MAP));
                                return;
                            }

                            if (response.code() == 409) {
                                etEmail.setError(getString(R.string.email_already_used));
                                etEmail.requestFocus();
                                return;
                            }

                            if (response.code() == 400) {
                                AppSnackbar.show(requireContext(), getString(R.string.invalid_registration_data));
                                return;
                            }

                            AppSnackbar.show(requireContext(), getString(R.string.registration_failed));
                        }

                        @Override
                        public void onFailure(Call<AuthResponse> call, Throwable t) {
                            setAuthLoading(btnSignUp, false);
                            if (!isAdded()) {
                                return;
                            }
                            Log.e(TAG, "Register request failed", t);
                            AppSnackbar.show(requireContext(), getString(R.string.network_error_try_again));
                        }
                    });
        });

        // Set link text to "Already have an account?"
        TextView tvSignInLink = view.findViewById(R.id.tv_signin_link);
        tvSignInLink.setText(R.string.already_have_account);
        tvSignInLink.setPaintFlags(tvSignInLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvSignInLink.setOnClickListener(v -> navController.navigate(loginUri));
    }

    private void setAuthLoading(Button button, boolean isLoading) {
        button.setEnabled(!isLoading);
    }
}
