package com.bif.app.feature.profile;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bif.app.core.network.RestApiService;
import com.bif.app.core.network.dto.profile.ProfileMetadataResponse;
import com.bif.app.core.network.dto.profile.UpdateMyProfileRequest;
import com.bif.app.core.utils.UserPreferences;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ApplicationContext;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileRepository {

    public static class LocalProfile {
        public final boolean isLoggedIn;
        public final String username;
        public final String email;
        public final String avatarUri;

        LocalProfile(boolean isLoggedIn, String username, String email, String avatarUri) {
            this.isLoggedIn = isLoggedIn;
            this.username = username;
            this.email = email;
            this.avatarUri = avatarUri;
        }
    }

    public interface ProfileCallback {
        void onSuccess();

        void onFailure();
    }

    private final Context appContext;
    private final RestApiService restApiService;

    @Inject
    public ProfileRepository(
            @ApplicationContext Context appContext,
            RestApiService restApiService
    ) {
        this.appContext = appContext;
        this.restApiService = restApiService;
    }

    public LocalProfile readLocalProfile() {
        return new LocalProfile(
                UserPreferences.isLoggedIn(appContext),
                safe(UserPreferences.getUsername(appContext)),
                safe(UserPreferences.getEmail(appContext)),
                safe(UserPreferences.getAvatarUri(appContext))
        );
    }

    public void saveAvatarUri(@NonNull String avatarUri) {
        UserPreferences.setAvatarUri(appContext, avatarUri);
    }

    public void syncProfileMetadata(@NonNull ProfileCallback callback) {
        if (!UserPreferences.isLoggedIn(appContext)) {
            callback.onSuccess();
            return;
        }

        restApiService.getMyProfileMetadata().enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProfileMetadataResponse> call,
                                   @NonNull Response<ProfileMetadataResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onSuccess();
                    return;
                }

                persistProfileFromRemote(response.body());
                callback.onSuccess();
            }

            @Override
            public void onFailure(@NonNull Call<ProfileMetadataResponse> call,
                                  @NonNull Throwable throwable) {
                callback.onFailure();
            }
        });
    }

    public void updateProfile(@NonNull String updatedUsername, @NonNull ProfileCallback callback) {
        restApiService.updateMyProfile(new UpdateMyProfileRequest(updatedUsername, null, null))
                .enqueue(new Callback<>() {
                    @Override
                    public void onResponse(@NonNull Call<ProfileMetadataResponse> call,
                                           @NonNull Response<ProfileMetadataResponse> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onFailure();
                            return;
                        }

                        persistProfileFromRemote(response.body());
                        callback.onSuccess();
                    }

                    @Override
                    public void onFailure(@NonNull Call<ProfileMetadataResponse> call,
                                          @NonNull Throwable throwable) {
                        callback.onFailure();
                    }
                });
    }

    private void persistProfileFromRemote(ProfileMetadataResponse profileMetadata) {
        String remoteDisplayName = sanitizeProfileValue(profileMetadata.displayName);
        String remoteEmail = sanitizeProfileValue(profileMetadata.email);

        if (!remoteDisplayName.isEmpty() || !remoteEmail.isEmpty()) {
            UserPreferences.saveUserProfile(appContext, remoteDisplayName, remoteEmail);
        }
    }

    private String sanitizeProfileValue(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}