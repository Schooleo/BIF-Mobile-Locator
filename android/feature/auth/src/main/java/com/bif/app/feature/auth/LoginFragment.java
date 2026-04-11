package com.bif.app.feature.auth;

import android.graphics.Paint;
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
import com.bif.app.core.network.dto.auth.LoginRequest;
import com.bif.app.core.utils.UriUtils;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@AndroidEntryPoint
public class LoginFragment extends Fragment {

    private static final String TAG = "LoginFragment";

    @Inject
    RestApiService restApiService;

    @Inject
    AuthSessionManager authSessionManager;

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
        EditText etEmail = view.findViewById(com.bif.app.core.R.id.et_input);
        EditText etPassword = view.findViewById(com.bif.app.core.R.id.et_password);


        // Set button text to "Sign in"
        Button btnLogin = view.findViewById(R.id.btn_login);
        btnLogin.setText(R.string.sign_in);
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty()) {
                etEmail.setError("Please enter email");
                etEmail.requestFocus();
                return;
            }

            if (password.isEmpty()) {
                etPassword.setError("Please enter password");
                etPassword.requestFocus();
                return;
            }

            setAuthLoading(btnLogin, true);

            restApiService.login(new LoginRequest(email, password)).enqueue(new Callback<>() {
                @Override
                public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                    setAuthLoading(btnLogin, false);
                    if (!isAdded()) {
                        return;
                    }

                    if (response.isSuccessful() && response.body() != null) {
                        AuthResponse auth = response.body();
                        authSessionManager.saveSessionFromAuth(auth);

                        AppSnackbar.show(requireContext(), "Login successful!");
                        navController.navigate(UriUtils.buildUri(UriUtils.PathTo.MAP));
                        return;
                    }

                    if (response.code() == 401) {
                        etPassword.setError("Incorrect email or password");
                        etPassword.requestFocus();
                        return;
                    }

                    if (response.code() == 400) {
                        AppSnackbar.show(requireContext(), "Invalid login data");
                        return;
                    }

                    AppSnackbar.show(requireContext(), "Login failed");
                }

                @Override
                public void onFailure(Call<AuthResponse> call, Throwable t) {
                    setAuthLoading(btnLogin, false);
                    if (!isAdded()) {
                        return;
                    }
                    Log.e(TAG, "Login request failed", t);
                    AppSnackbar.show(requireContext(), "Network error. Please try again.");
                }
            });
        });

        // Set link text to "Register"
        TextView tvRegisterLink = view.findViewById(R.id.tv_register_link);
        tvRegisterLink.setText(R.string.register);
        tvRegisterLink.setPaintFlags(tvRegisterLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        tvRegisterLink.setOnClickListener(v -> navController.navigate(UriUtils.buildUri("/register")));
    }

    private void setAuthLoading(Button button, boolean isLoading) {
        button.setEnabled(!isLoading);
    }
}
