package com.bif.app.core.auth;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.AuthResponse;
import com.bif.app.core.network.dto.auth.RefreshTokenRequest;
import com.bif.app.core.utils.UserPreferences;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthSessionManager {

    private static final String SYNC_PREF_NAME = "SYNC_PREF";

    public interface LogoutCallback {
        void onComplete(boolean remoteSuccess);
    }

    private final Context context;
    private final RestApiService restApiService;
    private final LocalSessionDataCleaner localSessionDataCleaner;

    @Inject
    public AuthSessionManager(
            @ApplicationContext Context context,
            RestApiService restApiService,
            LocalSessionDataCleaner localSessionDataCleaner
    ) {
        this.context = context;
        this.restApiService = restApiService;
        this.localSessionDataCleaner = localSessionDataCleaner;
    }

    public void saveSessionFromAuth(AuthResponse authResponse) {
        saveSessionFromAuth(authResponse, "", "");
    }

    public void saveSessionFromAuth(AuthResponse authResponse, String fallbackUsername, String fallbackEmail) {
        if (authResponse == null) {
            return;
        }

        String accessToken = authResponse.accessToken != null ? authResponse.accessToken : "";
        String refreshToken = authResponse.refreshToken != null ? authResponse.refreshToken : "";
        UserPreferences.saveAuthSession(context, accessToken, refreshToken);

        String userId = UserPreferences.getUserId(context);
        String username = fallbackUsername != null ? fallbackUsername : "";
        String email = fallbackEmail != null ? fallbackEmail : "";

        if (authResponse.user != null) {
            if (authResponse.user.id != null && !authResponse.user.id.isBlank()) {
                userId = authResponse.user.id.trim();
            }
            if (authResponse.user.username != null && !authResponse.user.username.isBlank()) {
                username = authResponse.user.username;
            }
            if (authResponse.user.email != null && !authResponse.user.email.isBlank()) {
                email = authResponse.user.email;
            }
        }

        if (!userId.isBlank()) {
            UserPreferences.setUserId(context, userId);
        }

        if (!username.isBlank() || !email.isBlank()) {
            UserPreferences.saveUserProfile(context, userId, username, email);
        }
    }

    public void clearSession() {
        UserPreferences.clearUser(context);
        clearPersistedSyncState();
        localSessionDataCleaner.clearLocalUserData();
    }

    private void clearPersistedSyncState() {
        context.getSharedPreferences(SYNC_PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    public void logout(@NonNull LogoutCallback callback) {
        String refreshToken = UserPreferences.getRefreshToken(context);
        if (refreshToken.isBlank()) {
            clearSession();
            callback.onComplete(false);
            return;
        }

        restApiService.logout(new RefreshTokenRequest(refreshToken)).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                clearSession();
                callback.onComplete(response.isSuccessful());
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                clearSession();
                callback.onComplete(false);
            }
        });
    }
}