package com.bif.app.feature.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;

@HiltViewModel
public class ProfileViewModel extends ViewModel {

    public static class ProfileUiState {
        public final boolean isLoggedIn;
        public final String usernameRaw;
        public final String emailRaw;
        public final String usernameForDisplay;
        public final String emailForDisplay;
        public final String avatarUri;
        public final String avatarInitial;

        ProfileUiState(
                boolean isLoggedIn,
                String usernameRaw,
                String emailRaw,
                String usernameForDisplay,
                String emailForDisplay,
                String avatarUri,
                String avatarInitial
        ) {
            this.isLoggedIn = isLoggedIn;
            this.usernameRaw = usernameRaw;
            this.emailRaw = emailRaw;
            this.usernameForDisplay = usernameForDisplay;
            this.emailForDisplay = emailForDisplay;
            this.avatarUri = avatarUri;
            this.avatarInitial = avatarInitial;
        }
    }

    private final Context appContext;
    private final ProfileRepository profileRepository;
    private final MutableLiveData<ProfileUiState> profileState = new MutableLiveData<>();
    private final MutableLiveData<Integer> messageResId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isRefreshing = new MutableLiveData<>(false);

    @Inject
    public ProfileViewModel(
            @ApplicationContext Context appContext,
            ProfileRepository profileRepository
    ) {
        this.appContext = appContext;
        this.profileRepository = profileRepository;
        loadFromLocal();
    }

    public LiveData<ProfileUiState> getProfileState() {
        return profileState;
    }

    public LiveData<Integer> getMessageResId() {
        return messageResId;
    }

    public LiveData<Boolean> getIsRefreshing() {
        return isRefreshing;
    }

    public void consumeMessage() {
        messageResId.setValue(null);
    }

    public void loadFromLocal() {
        ProfileRepository.LocalProfile localProfile = profileRepository.readLocalProfile();
        boolean isLoggedIn = localProfile.isLoggedIn;
        String usernameRaw = localProfile.username;
        String emailRaw = localProfile.email;
        String avatarUri = localProfile.avatarUri;

        String usernameForDisplay = getStoredValue(usernameRaw);
        String emailForDisplay = getStoredValue(emailRaw);
        String avatarInitial = resolveAvatarInitial(usernameRaw, emailRaw);

        profileState.setValue(new ProfileUiState(
                isLoggedIn,
                usernameRaw,
                emailRaw,
                usernameForDisplay,
                emailForDisplay,
                avatarUri,
                avatarInitial
        ));
    }

    public void onAvatarSelected(@NonNull String avatarUri) {
        profileRepository.saveAvatarUri(avatarUri);
        loadFromLocal();
        messageResId.setValue(R.string.avatar_updated);
    }

    public void refreshProfileFromServer() {
        refreshProfileFromServer(false);
    }

    public void refreshProfileFromServer(boolean showLoading) {
        if (showLoading) {
            isRefreshing.setValue(true);
        }

        profileRepository.syncProfileMetadata(new ProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess() {
                loadFromLocal();
                isRefreshing.setValue(false);
            }

            @Override
            public void onFailure() {
                isRefreshing.setValue(false);
                messageResId.setValue(R.string.profile_sync_failed);
            }
        });
    }

    public void updateProfile(@NonNull String updatedUsername) {
        profileRepository.updateProfile(updatedUsername, new ProfileRepository.ProfileCallback() {
            @Override
            public void onSuccess() {
                loadFromLocal();
                messageResId.setValue(R.string.profile_updated);
            }

            @Override
            public void onFailure() {
                messageResId.setValue(R.string.profile_update_failed);
            }
        });
    }

    private String getStoredValue(String value) {
        if (value == null) {
            return appContext.getString(R.string.not_available);
        }
        String trimmedValue = value.trim();
        return trimmedValue.isEmpty() ? appContext.getString(R.string.not_available) : trimmedValue;
    }

    private String resolveAvatarInitial(String username, String email) {
        if (username != null && !username.isBlank()) {
            return username.substring(0, 1).toUpperCase();
        }
        if (email != null && !email.isBlank()) {
            return email.substring(0, 1).toUpperCase();
        }
        return "G";
    }

}