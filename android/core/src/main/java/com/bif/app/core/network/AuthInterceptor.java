package com.bif.app.core.network;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bif.app.core.utils.UserPreferences;

import java.io.IOException;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final Context context;

    @Inject
    public AuthInterceptor(@ApplicationContext Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request originalRequest = chain.request();
        String path = originalRequest.url().encodedPath();

        if (isAuthEndpoint(path)) {
            return chain.proceed(originalRequest);
        }

        String token = UserPreferences.getAuthToken(context);

        if (!token.isEmpty()) {
            Request newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();

            // Send the modified request to the server
            return chain.proceed(newRequest);
        }

        // If no token, send the original request without headers.
        return chain.proceed(originalRequest);
    }

    private boolean isAuthEndpoint(String path) {
        return path.contains("/api/auth/login")
                || path.contains("/api/auth/register")
                || path.contains("/api/auth/refresh")
                || path.contains("/api/auth/logout")
                || path.contains("/api/auth/forgot-password");
    }
}