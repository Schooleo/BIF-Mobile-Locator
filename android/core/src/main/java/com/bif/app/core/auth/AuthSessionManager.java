package com.bif.app.core.auth;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.auth.AuthResponse;
import com.bif.app.core.network.dto.auth.RefreshTokenRequest;
import com.bif.app.core.utils.UserPreferences;
import com.bif.app.domain.sync.ISyncInitializable;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AuthSessionManager {

    private static final String TAG = "AuthSessionManager";

    public interface LogoutCallback {
        void onComplete(boolean remoteSuccess);
    }

    private final Context context;
    private final RestApiService restApiService;
    private final LocalSessionDataCleaner localSessionDataCleaner;
    private final ISyncInitializable syncInitializable;

    @Inject
    public AuthSessionManager(
            @ApplicationContext Context context,
            RestApiService restApiService,
            LocalSessionDataCleaner localSessionDataCleaner,
            ISyncInitializable syncInitializable
    ) {
        this.context = context;
        this.restApiService = restApiService;
        this.localSessionDataCleaner = localSessionDataCleaner;
        this.syncInitializable = syncInitializable;
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
            try {
                syncInitializable.setUserContext(userId, null);
                syncInitializable.setLastPulledVersion(0L);
                try {
                    syncInitializable.syncIfOnline();
                } catch (Exception syncException) {
                    Log.e(TAG, "Initial sync trigger failed, retry will happen on next sync cycle",
                            syncException);
                    try {
                        syncInitializable.syncIfOnline();
                    } catch (Exception retryException) {
                        Log.e(TAG, "Retrying initial sync trigger failed", retryException);
                    }
                }
            } catch (Exception syncInitException) {
                Log.e(TAG, "Failed to initialize sync context after authentication",
                        syncInitException);
                clearCorruptedSessionState();
                return;
            }
        }

        if (!username.isBlank() || !email.isBlank()) {
            UserPreferences.saveUserProfile(context, userId, username, email);
        }
    }

    public void clearSession() {
        clearSession(null);
    }

    public void clearSession(Runnable onComplete) {
        syncInitializable.resetSyncContext();
        UserPreferences.clearUser(context);
        localSessionDataCleaner.clearLocalUserData(onComplete);
    }

    public void logout(@NonNull LogoutCallback callback) {
        String refreshToken = UserPreferences.getRefreshToken(context);
        if (refreshToken.isBlank()) {
            clearSession(() -> callback.onComplete(false));
            return;
        }

        restApiService.logout(new RefreshTokenRequest(refreshToken)).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                clearSession(() -> callback.onComplete(response.isSuccessful()));
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable throwable) {
                clearSession(() -> callback.onComplete(false));
            }
        });
    }

    private void clearCorruptedSessionState() {
        try {
            UserPreferences.saveAuthSession(context, "", "");
        } catch (Exception tokenClearException) {
            Log.e(TAG, "Failed to clear persisted auth tokens", tokenClearException);
        }

        try {
            UserPreferences.clearUser(context);
        } catch (Exception profileClearException) {
            Log.e(TAG, "Failed to clear persisted user profile", profileClearException);
        }

        try {
            UserPreferences.setUserId(context, "");
        } catch (Exception userIdResetException) {
            Log.e(TAG, "Failed to reset persisted user id", userIdResetException);
        }

        try {
            syncInitializable.resetSyncContext();
        } catch (Exception syncResetException) {
            Log.e(TAG, "Failed to reset sync context", syncResetException);
        }

        try {
            localSessionDataCleaner.clearLocalUserData(null);
        } catch (Exception localDataClearException) {
            Log.e(TAG, "Failed to clear local session data", localDataClearException);
        }
    }
}