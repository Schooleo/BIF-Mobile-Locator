package com.bif.app.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bif.app.BuildConfig;
import com.bif.app.core.utils.UserPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;

public class TokenRefreshAuthenticator implements Authenticator {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final Context context;
    private final OkHttpClient refreshClient;

    @Inject
    public TokenRefreshAuthenticator(@ApplicationContext Context context) {
        this.context = context;
        this.refreshClient = new OkHttpClient.Builder().build();
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
        if (responseCount(response) >= 2 || isAuthEndpoint(response.request().url().encodedPath())) {
            return null;
        }

        String refreshToken = UserPreferences.getRefreshToken(context);
        if (refreshToken == null || refreshToken.isBlank()) {
            UserPreferences.clearUser(context);
            return null;
        }

        synchronized (this) {
            String latestToken = UserPreferences.getAuthToken(context);
            String requestToken = extractBearerToken(response.request().header("Authorization"));

            if (latestToken != null && !latestToken.isBlank() && requestToken != null && !latestToken.equals(requestToken)) {
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + latestToken)
                        .build();
            }

            Tokens refreshedTokens = requestNewTokens(refreshToken);
            if (refreshedTokens == null || refreshedTokens.accessToken.isBlank()) {
                UserPreferences.clearUser(context);
                return null;
            }

            UserPreferences.saveAuthSession(context, refreshedTokens.accessToken, refreshedTokens.refreshToken);

            return response.request().newBuilder()
                    .header("Authorization", "Bearer " + refreshedTokens.accessToken)
                    .build();
        }
    }

    private Tokens requestNewTokens(String refreshToken) throws IOException {
        JSONObject payload = new JSONObject();
        try {
            payload.put("refreshToken", refreshToken);
        } catch (JSONException e) {
            return null;
        }

        Request request = new Request.Builder()
                .url(BuildConfig.REST_BASE_URL + "auth/refresh")
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        try (Response refreshResponse = refreshClient.newCall(request).execute()) {
            if (!refreshResponse.isSuccessful()) {
                return null;
            }

            ResponseBody body = refreshResponse.body();
            if (body == null) {
                return null;
            }

            JSONObject json = new JSONObject(body.string());
            String newAccessToken = json.optString("accessToken", "");
            String newRefreshToken = json.optString("refreshToken", refreshToken);
            return new Tokens(newAccessToken, newRefreshToken);
        } catch (JSONException e) {
            return null;
        }
    }

    private boolean isAuthEndpoint(String path) {
        return path.contains("/api/auth/login")
                || path.contains("/api/auth/register")
                || path.contains("/api/auth/refresh")
                || path.contains("/api/auth/logout");
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }

    private int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) {
            count++;
        }
        return count;
    }

    private static class Tokens {
        final String accessToken;
        final String refreshToken;

        Tokens(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }
}
