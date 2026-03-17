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
        // Grab the outgoing request
        Request originalRequest = chain.request();

        // Fetch token
        String token = UserPreferences.getAuthToken(context);

        // If the user is logged in, attach the Authorization header
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
}